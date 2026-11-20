/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.rendering.terrain.TerrainBinding
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A scene sets the geometry clipmap its terrain draws with: rings, grid and spacing (#1809). */
class TerrainClipmapFieldsTest {
    private val json = SceneSerializers.createJson()

    @Test
    fun aTerrainWithoutTheFieldsDrawsWithTheClipmapItAlwaysHad() {
        assertEquals(TerrainClipmapConfig(), attach(FLAT).clipmapConfig)
        val older = json.decodeFromString(SceneTerrain.serializer(), """{"width":2,"depth":2,"samples":[0,0,0,0]}""")
        assertEquals(TerrainClipmapConfig(), attach(older).clipmapConfig, "a document from before the fields")
    }

    @Test
    fun theFieldsReachTheTerrainAndSaveBack() {
        val scene = FLAT.copy(clipmapRings = 8, clipmapRingResolution = 128, clipmapBaseSpacing = 4f)
        val world = World()
        val entity = world.create()
        TerrainBinding.attachTyped(world, entity, scene, Context(world))
        val terrain = assertNotNull(world.get<TerrainComponent>(entity))
        assertEquals(TerrainClipmapConfig(ringCount = 8, ringResolution = 128, baseSpacing = 4f), terrain.clipmapConfig)

        assertEquals(scene, TerrainBinding.export(world, entity, terrain))
        val saved = json.encodeToString(SceneTerrain.serializer(), scene)
        assertTrue(""""clipmapRings":8""" in saved && """"clipmapRingResolution":128""" in saved, saved)
        assertEquals(scene, json.decodeFromString(SceneTerrain.serializer(), saved))
    }

    @Test
    fun aClipmapTheShaderOrTheGeometryCannotDrawIsReported() {
        fun issues(terrain: SceneTerrain) = terrain.validate("nodes[0]").map { it.message }
        assertEquals(emptyList(), issues(FLAT.copy(clipmapRings = 8, clipmapRingResolution = 256, clipmapBaseSpacing = 0.5f)))
        assertEquals(1, issues(FLAT.copy(clipmapRings = 1)).size)
        assertEquals(1, issues(FLAT.copy(clipmapRings = 9)).size, "the terrain shader reserves 8 ring slots")
        assertEquals(1, issues(FLAT.copy(clipmapRingResolution = 63)).size, "an odd grid leaves seams")
        assertEquals(1, issues(FLAT.copy(clipmapRingResolution = 8)).size)
        assertEquals(1, issues(FLAT.copy(clipmapRingResolution = 512)).size)
        assertEquals(1, issues(FLAT.copy(clipmapBaseSpacing = 0f)).size)
        assertEquals(1, issues(FLAT.copy(clipmapBaseSpacing = Float.NaN)).size)
    }

    /**
     * The issue's measure: how far apart the drawn vertices are 1 km from the camera, read from the
     * merged clipmap mesh the terrain draws. Defaults give 32 m there; each doubling of the grid halves it.
     */
    @Test
    fun aFinerGridDrawsCloserVerticesAKilometreOut() {
        val spacing = listOf(64, 128, 256).associateWith { resolution ->
            val config = attach(FLAT.copy(clipmapRingResolution = resolution)).clipmapConfig
            drawnSpacingAt(TerrainClipmapGeometry.buildMergedClipmapMesh(config).let { it.vertices to it.indices }, KILOMETRE)
        }
        println("Clipmap vertex spacing 1 km out, by cells per side: $spacing")
        assertEquals(mapOf(64 to 32f, 128 to 16f, 256 to 8f), spacing)
    }

    /** The counts the terrain guide's table lists: one draw whatever the setting, these vertices and triangles. */
    @Test
    fun theGuidesCountsAreTheMeshes() {
        val listed = mapOf(
            TerrainClipmapConfig() to (25_350 to 41_312),
            TerrainClipmapConfig(ringCount = 8, ringResolution = 128) to (133_128 to 211_744),
            TerrainClipmapConfig(ringCount = 8, ringResolution = 256) to (528_392 to 833_312),
        )
        listed.forEach { (config, counts) ->
            val mesh = TerrainClipmapGeometry.buildMergedClipmapMesh(config)
            assertEquals(counts, mesh.vertices.size / STRIDE to mesh.indices.size / 3, "$config")
        }
    }

    /** The shortest edge of any drawn triangle that holds the ground point [x] metres along +X. */
    private fun drawnSpacingAt(mesh: Pair<FloatArray, IntArray>, x: Float): Float {
        val (vertices, indices) = mesh
        fun px(i: Int) = vertices[i * STRIDE]
        fun pz(i: Int) = vertices[i * STRIDE + 2]
        var finest = Float.MAX_VALUE
        for (t in indices.indices step 3) {
            val a = indices[t]
            val b = indices[t + 1]
            val c = indices[t + 2]
            if (!contains(px(a), pz(a), px(b), pz(b), px(c), pz(c), x, PROBE_Z)) continue
            val edge = max(abs(px(a) - px(b)), abs(pz(a) - pz(b))).coerceAtLeast(max(abs(px(b) - px(c)), abs(pz(b) - pz(c))))
            finest = minOf(finest, edge)
        }
        return finest
    }

    private fun contains(ax: Float, az: Float, bx: Float, bz: Float, cx: Float, cz: Float, x: Float, z: Float): Boolean {
        fun side(px: Float, pz: Float, qx: Float, qz: Float) = (qx - px) * (z - pz) - (qz - pz) * (x - px)
        val d1 = side(ax, az, bx, bz)
        val d2 = side(bx, bz, cx, cz)
        val d3 = side(cx, cz, ax, az)
        return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0))
    }

    private fun attach(scene: SceneTerrain): TerrainComponent {
        val world = World()
        val entity = world.create()
        TerrainBinding.attachTyped(world, entity, scene, Context(world))
        return assertNotNull(world.get<TerrainComponent>(entity))
    }

    private class Context(override val world: World) : SceneResolutionContext {
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

        override fun recordRequest(request: Any) = Unit
    }

    private companion object {
        val FLAT = SceneTerrain(width = 2, depth = 2, samples = listOf(0f, 0f, 0f, 0f))
        const val KILOMETRE = 1000f

        /** Off every grid line, so the point falls inside one triangle of a level rather than on an edge. */
        const val PROBE_Z = 0.3f

        /** The clipmap's vertex format, position first. */
        val STRIDE = VertexFormat.PositionNormalColorUv.strideFloats
    }
}
