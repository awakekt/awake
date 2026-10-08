/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldstream

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.HeightmapSampleEdit
import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.asset.terrain.TerrainPageLayout
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.world.WorldOrigin
import com.awakekt.awake.terrain.PagedTerrain
import com.awakekt.awake.terrain.TerrainPage
import com.awakekt.awake.terrain.TerrainPageStreamer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PagedTerrainSystemTest {
    @Test fun collidersUseCurrentOriginAndAreRebuiltAndRetiredWithTheirPages() = runTest {
        val world = World()
        world.add(world.create(), WorldOrigin(stepX = 1, quantum = 100f))
        val terrain = PagedTerrain(
            PagedHeightmap(
                TerrainPageLayout(25, 0, 2, 1, 4f, 4, maxElevation = 10f),
                Heightmap(FloatArray(6), 3, 2, Vec3f(4f, 1f, 4f)),
            ),
            2,
        )
        val streamer = TerrainPageStreamer(terrain, this, { TerrainPage(Heightmap(FloatArray(25) { 1f }, 5, 5, Vec3f(1f, 1f, 1f))) }, radius = 0, maxConcurrentReads = 1)
        val physics = RecordingPhysicsWorld()
        val physicsSystem = PhysicsSystem(physics)
        var eye = Vec3f(2f, 10f, 2f)
        val system = PagedTerrainSystem(streamer, { eye }, physicsSystem, collisionRadius = 0)
        val cell = TerrainPageCoord(25, 0)
        system.update(world, 0f)
        advanceUntilIdle()
        system.update(world, 0f)
        assertFalse(system.collisionReady(world, cell))
        physicsSystem.update(world, 0f)
        assertTrue(system.collisionReady(world, cell))
        assertEquals(Vec3f(2f, -0f, 2f), physics.positions.single())
        terrain.editHeights(listOf(HeightmapSampleEdit(2, 2, 3f)))
        system.update(world, 0f)
        assertFalse(system.collisionReady(world, cell))
        assertEquals(1, physics.destroyed.size)
        physicsSystem.update(world, 0f)
        terrain.acknowledgeSave(terrain.saveSnapshot(cell))
        eye = Vec3f(6f, 10f, 2f)
        system.update(world, 0f)
        assertFalse(system.collisionReady(world, cell))
        advanceUntilIdle()
        system.update(world, 0f)
        physicsSystem.update(world, 0f)
        assertEquals(1, physics.liveBodies.size)
        system.close(world)
        assertTrue(physics.liveBodies.isEmpty())
        assertTrue(terrain.residentCoords.isEmpty())
    }

    @Test fun sceneOptionsValidateAndMapWithoutWholeWorldSamples() = runTest {
        val config = ScenePagedTerrain("world.terrainpages.json", capacity = 2, radius = 0, maxConcurrentReads = 1, maxUploadBytes = 512, collisionRadius = 0)
        val terrain = PagedTerrain(PagedHeightmap(TerrainPageLayout(0, 0, 1, 1, 4f, 4), Heightmap(FloatArray(4), 2, 2, Vec3f(4f, 1f, 4f))), 2, maxUploadBytes = 512)
        val streamer = config.streamer(terrain, this, { null })
        assertEquals(config.radius, streamer.radius)
        assertEquals(config.maxConcurrentReads, streamer.maxConcurrentReads)
        assertTrue(config.validate("terrain").isEmpty())
        assertTrue(config.copy(maxConcurrentReads = 3).validate("terrain").isNotEmpty())
        assertTrue(config.copy(collisionRadius = 1).validate("terrain").isNotEmpty())
        streamer.close()
    }
}
