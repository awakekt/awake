/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapTracker
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.Camera

/**
 * ECS System managing real-time Geometry Clipmap tracking for all active [TerrainComponent]s.
 *
 * Each frame:
 * 1. Resolves the primary [Camera] in the active [World].
 * 2. Updates the [TerrainClipmapTracker] to compute discrete snapped origins per LOD ring.
 * 3. Keeps concentric rings centered on the observer without vertex shimmering.
 */
class TerrainClipmapSystem : System {

    val trackersByTerrain = HashMap<TerrainComponent, TerrainClipmapTracker>()

    override fun update(world: World, delta: Float) {
        var cameraPos: Vec3f? = null
        world.queryEach(Camera::class) { _, camera ->
            if (camera.isPrimary || cameraPos == null) {
                cameraPos = camera.lens.eye
            }
        }

        val activeCamPos = cameraPos ?: return

        world.queryEach(TerrainComponent::class) { _, terrain ->
            if (!terrain.isVisible) return@queryEach

            val tracker = trackersByTerrain.getOrPut(terrain) {
                TerrainClipmapTracker(terrain.clipmapConfig)
            }

            tracker.update(activeCamPos)
        }
    }

    fun trackerFor(terrain: TerrainComponent): TerrainClipmapTracker? = trackersByTerrain[terrain]

    fun clear() {
        trackersByTerrain.clear()
    }
}
