/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.asset.terrain.clipmap.TerrainClipmapConfig
import io.github.awakelab.awake.asset.terrain.splat.TerrainSplatWeightMap
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.rendering.Camera
import io.github.awakelab.awake.scene.rendering.terrain.TerrainComponent
import io.github.awakelab.awake.scene.rendering.terrain.TerrainClipmapSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            )
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

        // 2. Add terrain component with 4-ring clipmap config
        val terrainEntity = world.create()
        val terrain = TerrainComponent(
            heightmap = Heightmap(FloatArray(128 * 128), 128, 128, Vec3f.ONE),
            clipmapConfig = TerrainClipmapConfig(ringCount = 4, ringResolution = 32, baseSpacing = 1.0f),
        )
        world.add(terrainEntity, terrain)

        // 3. Update system
        system.update(world, delta = 0.016f)

        val tracker = system.trackerFor(terrain)
        assertNotNull(tracker)
        val rings = tracker.ringStates
        assertEquals(4, rings.size)

        // Level 0 (spacing 1.0) snapped center should be near (129, 0, 256)
        assertEquals(129.0f, rings[0].snappedCenter.x, 0.1f)
        assertEquals(256.0f, rings[0].snappedCenter.z, 0.1f)

        // Level 1 (spacing 2.0) snapped center should be (128, 0, 256)
        assertEquals(128.0f, rings[1].snappedCenter.x, 0.1f)
        assertEquals(256.0f, rings[1].snappedCenter.z, 0.1f)
    }
}
