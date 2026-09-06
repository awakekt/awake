/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.spatial

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.spatial.findSpatialIndex

/**
 * Keeps the scene's [SpatialIndex] current, so [RenderSystem] can ask which entities are in
 * front of the camera instead of testing every one of them.
 *
 * Install it before [RenderSystem] -- what it writes this frame is what culling reads this
 * frame. A scene without it has no index and [RenderSystem] falls back to testing each entity,
 * which is what it did before this existed.
 *
 * Indexes exactly the entities `MeshBounds` already opts in: bounds are what both the index and
 * the culling test need, and an entity without them is never culled anyway.
 *
 * ### What it costs, measured
 *
 * Do not install this expecting free frames. At 10k props spread over 2km
 * (`SpatialCullingBenchmarks`), one frame's numbers are:
 *
 * - frustum query through the grid: **6.5us**
 * - the same answer by testing every entity against a shared plane list: **34us**
 * - keeping this index current for those 10k entities: **67us**
 *
 * So for culling ALONE the index loses: 73us of query plus maintenance against 34us of plain
 * scanning. Maintenance is a full pass because this ECS reports neither movement nor
 * destruction, so the only way to know an entity moved is to look.
 *
 * It wins when several queries share that one pass -- culling plus AI range queries plus
 * picking, say -- or when the scene grows enough that scanning dominates. One query per frame is
 * not that case, which is why nothing installs this by default.
 *
 * Entities that no longer exist are dropped by comparing the indexed set against the live one,
 * rather than by hooking destruction: nothing in this ECS reports a destroyed entity, and a
 * stale entry would be returned by every query until something noticed.
 */
class SpatialIndexSystem : System {

    /** Ids seen this frame, to find the ones the grid still holds and the world no longer has. */
    private val live = HashSet<Int>()
    private val stale = ArrayList<Int>()

    override fun update(world: World, delta: Float) {
        val grid = (world.findSpatialIndex() ?: install(world)).grid
        live.clear()
        world.family<Transform, MeshBounds>().forEach { entity, transform, bounds ->
            live.add(entity.id)
            // Cheap for a still entity, and the only place the transformed box is built for a
            // moved one -- RenderSystem reads the same cache a moment later.
            grid.insert(entity.id, bounds.worldBounds(transform.worldMatrix))
        }
        if (grid.size == live.size) return
        stale.clear()
        grid.ids.forEach { id -> if (id !in live) stale.add(id) }
        stale.forEach(grid::remove)
    }

    private fun install(world: World): SpatialIndex {
        val index = SpatialIndex(SpatialGrid())
        world.add(world.create(), index)
        return index
    }
}
