/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.terrain.TerrainClipmapSystem
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class TerrainClipmapSystemTest {

    @Test
    fun prunesTrackersWhenTerrainsAreRemovedWhileKeepingHiddenTerrains() {
        val world = World()
        val camera = world.create()
        world.add(
            camera,
            Camera(
                lens = Lens(
                    eye = Vec3f(0f, 10f, 0f),
                    center = Vec3f(0f, 0f, -1f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 100f,
                ),
                isPrimary = true,
            ),
        )

        val firstEntity = world.create()
        val firstTerrain = TerrainComponent(heightmap = Heightmap(FloatArray(4), 2, 2, Vec3f(1f, 1f, 1f)))
        world.add(firstEntity, firstTerrain)

        val secondEntity = world.create()
        val secondTerrain = TerrainComponent(heightmap = Heightmap(FloatArray(4), 2, 2, Vec3f(1f, 1f, 1f)))
        world.add(secondEntity, secondTerrain)

        val system = TerrainClipmapSystem()
        system.update(world, 1f / 60f)
        assertEquals(2, system.trackersByTerrain.size)
        val firstTracker = assertNotNull(system.trackerFor(firstTerrain))
        assertNotNull(system.trackerFor(secondTerrain))

        // Hiding a terrain keeps its tracker until the component or entity is actually removed.
        firstTerrain.isVisible = false
        world.destroy(secondEntity)
        system.update(world, 1f / 60f)
        assertEquals(1, system.trackersByTerrain.size)
        assertSame(firstTracker, system.trackerFor(firstTerrain))
        assertNull(system.trackerFor(secondTerrain))

        // Showing it again re-buckets the existing tracker instead of duplicating it.
        firstTerrain.isVisible = true
        system.update(world, 1f / 60f)
        assertEquals(1, system.trackersByTerrain.size)
        assertSame(firstTracker, system.trackerFor(firstTerrain))

        world.remove<TerrainComponent>(firstEntity)
        system.update(world, 1f / 60f)
        assertEquals(0, system.trackersByTerrain.size)
        assertNull(system.trackerFor(firstTerrain))
    }
}
