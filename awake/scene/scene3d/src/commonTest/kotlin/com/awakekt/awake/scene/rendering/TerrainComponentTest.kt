/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.splat.TerrainSplatWeightMap
import com.awakekt.awake.core.math.GridOrigin
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.terrain.TerrainClipmapSystem
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TerrainComponentTest {

    @Test
    fun terrainComponentAttachesToEntityAndCanBeQueried() {
        val world = World()
        val heightmap = Heightmap(FloatArray(64 * 64), 64, 64, Vec3f.ONE)
        val splatMap = TerrainSplatWeightMap(16, 16, ByteArray(16 * 16 * 4) { 64.toByte() })

        val entity = world.create()
        world.add(
            entity,
            TerrainComponent(
                heightmap = heightmap,
                splatMap = splatMap,
                tilingScale = 32.0f,
            ),
        )

        val retrieved = world.get<TerrainComponent>(entity)
        assertNotNull(retrieved)
        assertEquals(64, retrieved.heightmap.width)
        assertEquals(16, retrieved.splatMap?.width)
        assertEquals(32.0f, retrieved.tilingScale)
    }

    @Test
    fun clipmapSystemTracksPrimaryCameraAndUpdatesRings() {
        val world = World()
        val system = TerrainClipmapSystem()

        // 1. Add primary camera at (128.5, 50, 256.2)
        val camEntity = world.create()
        val lens = Lens.perspective(eye = Vec3f(128.5f, 50.0f, 256.2f))
        world.add(camEntity, Camera(lens = lens, isPrimary = true))

        // 2. Add terrain component with 4-ring clipmap config, the camera over it: rings stay on
        // the map, so a camera off it would see them clamped to its edge instead.
        val terrainEntity = world.create()
        val terrain = TerrainComponent(
            heightmap = Heightmap(FloatArray(300 * 300), 300, 300, Vec3f.ONE, GridOrigin.Corner),
            clipmapConfig = TerrainClipmapConfig(ringCount = 4, ringResolution = 32, baseSpacing = 1.0f),
        )
        world.add(terrainEntity, terrain)

        // 3. Update system
        system.update(world, delta = 0.016f)

        val tracker = system.trackerFor(terrain)
        assertNotNull(tracker)
        val rings = tracker.ringStates
        assertEquals(4, rings.size)

        // Each level's corner snaps to the next level's grid (twice its spacing), so its centre is
        // the nearest such position to the camera: (128, 256) for level 0 (corners on 2 m steps)...
        assertEquals(128.0f, rings[0].snappedCenter.x, 0.1f)
        assertEquals(256.0f, rings[0].snappedCenter.z, 0.1f)

        // ...and for level 1 (corners on 4 m steps).
        assertEquals(128.0f, rings[1].snappedCenter.x, 0.1f)
        assertEquals(256.0f, rings[1].snappedCenter.z, 0.1f)
    }
}
