/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.normalAt
import com.awakekt.awake.asset.terrain.toPositionNormalColorMesh
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.terrain.TerrainBinding
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Terrain" guide shows one small hill as a scene document and in the scene DSL, and the
 * heightmap calls behind it. Every sample is included from here.
 */
class TerrainDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSameTerrain() {
        val world = World()
        // --8<-- [start:terrain-dsl]
        world.scene {
            entity("hill") {
                with(
                    TerrainComponent(
                        heightmap = Heightmap(
                            samples = floatArrayOf(
                                0f, 0f, 0f, 0f,
                                0f, 1.5f, 1f, 0f,
                                0f, 1f, 0.5f, 0f,
                                0f, 0f, 0f, 0f,
                            ),
                            width = 4,
                            depth = 4,
                            scale = Vec3f(2f, 1f, 2f),
                        ),
                        tilingScale = 16f,
                    ),
                )
            }
        }
        // --8<-- [end:terrain-dsl]
        val fromDsl = world.terrains().single()

        DefaultSceneComponentResolvers.install()
        val document = SceneLoader.decode(File(DOCS_SNIPPETS, "world/hill.scene.json").readText())
        val loaded = SceneLoader.instantiate(document).world
        val fromDocument = loaded.terrains().single()

        // TerrainComponent holds a Heightmap, which has no value equality; compare what a save writes.
        assertEquals(
            TerrainBinding.export(loaded, loaded.query(TerrainComponent::class).single(), fromDocument),
            TerrainBinding.export(world, world.query(TerrainComponent::class).single(), fromDsl),
        )
        assertEquals(1.5f, fromDsl.heightmap.heightAt(1, 1))
    }

    @Test
    fun aHeightmapBuildsAMeshAndAnswersHeights() {
        // --8<-- [start:heightmap]
        val heightmap = Heightmap(
            samples = FloatArray(33 * 33) { i -> if (i % 33 in 12..20 && i / 33 in 12..20) 2f else 0f },
            width = 33,
            depth = 33,
            scale = Vec3f(1f, 1f, 1f), // metres between samples on X and Z, and a height multiplier on Y
        )
        // Centred: this map spans -16..16 on X and Z.
        val groundY = heightmap.heightAtWorld(0f, 0f) // NaN outside the map
        val up = heightmap.normalAt(0, 0)
        val mesh = heightmap.toPositionNormalColorMesh { _, _, height -> if (height > 1f) Color.White else Color.Black }
        // --8<-- [end:heightmap]

        assertEquals(2f, groundY)
        assertEquals(1f, up.y, 1e-6f)
        assertTrue(heightmap.heightAtWorld(100f, 0f).isNaN())
        assertEquals((33 - 1) * (33 - 1) * 6, mesh.indices.size)
    }

    @Test
    fun aSculptedCopyLeavesTheOriginalAlone() {
        val heightmap = Heightmap(FloatArray(16), width = 4, depth = 4, scale = Vec3f(1f, 1f, 1f))
        // --8<-- [start:sculpt]
        val editable = heightmap.mutableCopy()
        val change = editable.setHeightAt(x = 1, z = 2, height = 3f) // null when nothing changed
        val sculpted = editable.snapshot() // immutable again: build a mesh or collider from this
        // --8<-- [end:sculpt]

        assertEquals(3f, sculpted.heightAt(1, 2))
        assertEquals(0f, heightmap.heightAt(1, 2))
        assertTrue(change != null)
    }

    private fun World.terrains(): List<TerrainComponent> =
        query(TerrainComponent::class).mapNotNull { get<TerrainComponent>(it) }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
