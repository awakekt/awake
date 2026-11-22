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
import com.awakekt.awake.scene.rendering.camera.Camera

/**
 * ECS System managing real-time Geometry Clipmap tracking for all active [TerrainComponent]s.
 *
 * Each frame:
 * 1. Resolves the primary [Camera] in the active [World].
 * 2. Updates the [TerrainClipmapTracker] to compute discrete snapped origins per LOD ring.
 * 3. Keeps concentric rings centered on the observer without vertex shimmering.
 */
class TerrainClipmapSystem : System {

    /** Active map of clipmap ring trackers keyed by terrain component. */
    val trackersByTerrain = HashMap<TerrainComponent, TerrainClipmapTracker>()

    private val activeTerrains = ArrayList<TerrainComponent>()
    private val origin = Vec3f()
    private val focus = Vec3f()

    override fun update(world: World, delta: Float) {
        var cameraPos: Vec3f? = null
        world.queryEach<Camera> { _, camera ->
            if (camera.isPrimary || cameraPos == null) {
                cameraPos = camera.lens.eye
            }
        }

        val activeCamPos = cameraPos ?: return

        activeTerrains.clear()
        world.queryEach<TerrainComponent> { entity, terrain ->
            activeTerrains += terrain
            if (!terrain.isVisible) return@queryEach

            val tracker = findOrCreateTracker(terrain)
            terrainOrigin(world, entity, origin)
            focus.set(activeCamPos.x - origin.x, activeCamPos.y - origin.y, activeCamPos.z - origin.z)
            tracker.update(focus)
        }
        pruneRemovedTerrains()
        activeTerrains.clear()
    }

    /**
     * Retrieves the clipmap ring tracker associated with the given [terrain] component.
     *
     * @param terrain The target terrain component.
     * @return The active [TerrainClipmapTracker], or `null` if none has been registered yet.
     */
    fun trackerFor(terrain: TerrainComponent): TerrainClipmapTracker? =
        trackersByTerrain[terrain] ?: trackersByTerrain.entries.firstOrNull { it.key === terrain }?.value

    /** Clears all cached clipmap ring trackers from this system. */
    fun clear() {
        trackersByTerrain.clear()
    }

    // TerrainComponent is a mutable data class (isVisible/revision change hashCode), so fall back to
    // identity lookup and re-bucket if its hash shifted instead of creating a duplicate tracker.
    private fun findOrCreateTracker(terrain: TerrainComponent): TerrainClipmapTracker {
        var found = trackersByTerrain[terrain]
        if (found == null) {
            val entries = trackersByTerrain.entries.iterator()
            while (entries.hasNext() && found == null) {
                val entry = entries.next()
                if (entry.key === terrain) {
                    found = entry.value
                    entries.remove()
                    trackersByTerrain[terrain] = found
                }
            }
        }
        return found ?: TerrainClipmapTracker(terrain.clipmapConfig, terrain.heightmap).also { trackersByTerrain[terrain] = it }
    }

    private fun pruneRemovedTerrains() {
        val entries = trackersByTerrain.entries.iterator()
        while (entries.hasNext()) {
            val key = entries.next().key
            if (activeTerrains.none { it === key }) entries.remove()
        }
    }
}
