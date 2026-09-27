/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.rendering.terrain.TerrainBinding
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.scene.rendering.terrain.TerrainSurfaceReference
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TerrainBindingTest {

    init {
        SceneComponentRegistry.registerGlobal(TerrainBinding)
    }

    @Test
    fun aDecodedTerrainAttachesItsHeightmapAndSurfaceReference() {
        val world = World()
        val entity = world.create()

        TerrainBinding.attachTyped(world, entity, decode(LAYERED_TERRAIN), Context(world))

        val terrain = assertNotNull(world.get<TerrainComponent>(entity))
        assertEquals(3, terrain.heightmap.width)
        assertEquals(2, terrain.heightmap.depth)
        assertEquals(4f, terrain.heightmap.copySamples()[3])
        assertEquals(0.5f, terrain.heightmap.scale.y)
        assertEquals(8f, terrain.tilingScale)
        val surface = assertNotNull(world.get<TerrainSurfaceReference>(entity))
        assertEquals("example.layers", surface.provider)
        assertEquals(2, surface.version)
        assertEquals(PAYLOAD, surface.payload)
    }

    /** Core does not read a provider's payload, so it must come back byte for byte. */
    @Test
    fun exportingReproducesTheDecodedTerrainIncludingItsPayload() {
        val world = World()
        val entity = world.create()
        val decoded = decode(LAYERED_TERRAIN)
        TerrainBinding.attachTyped(world, entity, decoded, Context(world))

        val exported = TerrainBinding.export(world, entity, assertNotNull(world.get<TerrainComponent>(entity)))

        assertEquals(decoded, exported)
    }

    /** Studio's earlier terrain carried authoring fields Core does not model; they are skipped, not fatal. */
    @Test
    fun aTerrainWithFieldsCoreDoesNotKnowStillDecodes() {
        val terrain = decode(
            """
            {"component": "terrain", "width": 2, "depth": 2, "samples": [0, 1, 2, 3],
             "assetPath": "terrain/landscape.terrain.json", "layers": ["grass.png"],
             "splatMapPath": "splat.png", "colorMapPath": "color.png"}
            """,
        )

        assertEquals(listOf(0f, 1f, 2f, 3f), terrain.samples)
        assertNull(terrain.surface)
    }

    @Test
    fun validationReportsWhatHeightmapWouldReject() {
        val terrain = SceneTerrain(width = 3, depth = 1, scaleY = 0f, samples = listOf(0f, Float.NaN))

        assertEquals(4, terrain.validate("nodes[0]").size)
        assertEquals(emptyList(), decode(LAYERED_TERRAIN).validate("nodes[0]"))
    }

    private fun decode(component: String): SceneTerrain =
        SceneLoader.decode("""{"version": 1, "nodes": [{"components": [$component]}]}""")
            .nodes.single().components.single() as SceneTerrain

    private class Context(override val world: World) : SceneResolutionContext {
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

        override fun recordRequest(request: Any) = Unit
    }

    private companion object {
        val PAYLOAD = buildJsonObject {
            put("palette", "terrain/region.terrainpalette.json")
            putJsonArray("tiles") { add("a") }
        }

        val LAYERED_TERRAIN = """
            {"component": "terrain", "width": 3, "depth": 2, "scaleY": 0.5,
             "samples": [0, 1, 2, 4, 5, 6], "tilingScale": 8,
             "surface": {"provider": "example.layers", "version": 2, "payload": $PAYLOAD}}
        """
    }
}
