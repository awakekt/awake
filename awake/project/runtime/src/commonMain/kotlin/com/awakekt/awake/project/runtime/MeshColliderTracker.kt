/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.EntityList
import com.awakekt.awake.ecs.EntitySet
import com.awakekt.awake.ecs.Family2
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.MeshCollider
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem

/**
 * Tracks static [MeshCollider] entities for [StaticColliderStreamSystem], parking distant
 * components off entities and restoring them when the observer approaches within `meshReach`.
 */
internal class MeshColliderTracker(
    private val physics: PhysicsSystem,
    config: ColliderStreamConfig,
    private val originScratch: Vec3f,
) {
    private val reachSq = config.meshReach * config.meshReach
    private val leaveSq = config.meshLeave * config.meshLeave
    private val entries = ArrayList<Entry>()
    private val tracked = EntitySet()
    var parkedCount: Int = 0
        private set

    val trackedCount: Int get() = entries.size

    fun discover(family: Family2<Transform, MeshCollider>) {
        family.forEach { entity, _, _ ->
            if (entity !in tracked) {
                tracked.add(entity)
                entries.add(Entry(entity))
            }
        }
    }

    @Suppress("LongParameterList") // Avoids allocating a context object per mesh evaluation pass.
    fun evaluate(
        world: World,
        startIndex: Int,
        obsX: Float,
        obsZ: Float,
        transformTypeId: ComponentTypeId,
        activated: EntityList?,
    ) {
        var index = entries.lastIndex
        while (index >= startIndex) {
            val entry = entries[index]
            val entity = entry.entity
            val saved = entry.parkedCollider
            val alive = world.isAlive(entity) && world.has<Transform>(entity)
            val activeCollider = if (alive && saved == null) world.get<MeshCollider>(entity) else null
            if (!alive || (saved == null && activeCollider == null)) {
                removeEntryAt(index, entry)
            } else {
                stepEntry(world, entry, saved, activeCollider, obsX, obsZ, transformTypeId, activated)
            }
            index--
        }
    }

    fun prune(world: World, limit: Int) {
        var index = minOf(limit, entries.size) - 1
        while (index >= 0) {
            val entry = entries[index]
            val entity = entry.entity
            val keep = world.isAlive(entity) &&
                world.has<Transform>(entity) &&
                (entry.parkedCollider != null || world.has<MeshCollider>(entity))
            if (!keep) removeEntryAt(index, entry)
            index--
        }
    }

    fun clear() {
        entries.clear()
        tracked.clear()
        parkedCount = 0
    }

    @Suppress("LongParameterList") // Avoids allocating a context object per mesh evaluation.
    private fun stepEntry(
        world: World,
        entry: Entry,
        saved: MeshCollider?,
        activeCollider: MeshCollider?,
        obsX: Float,
        obsZ: Float,
        transformTypeId: ComponentTypeId,
        activated: EntityList?,
    ) {
        val entity = entry.entity
        val origin = resolveStaticOrigin(world, entity, transformTypeId, originScratch)
        val dx = obsX - origin.x
        val dz = obsZ - origin.z
        val distSq = dx * dx + dz * dz
        if (saved != null) {
            if (distSq <= reachSq) {
                entry.parkedCollider = null
                entry.initialized = true
                parkedCount--
                if (!world.has<MeshCollider>(entity)) world.add(entity, saved)
                activated?.add(entity)
            }
        } else if (activeCollider != null) {
            val maxDistSq = if (entry.initialized || world.has<PhysicsBody>(entity)) leaveSq else reachSq
            entry.initialized = true
            if (distSq > maxDistSq) {
                physics.destroyBody(world, entity)
                world.remove<PhysicsBody>(entity)
                world.remove<MeshCollider>(entity)
                entry.parkedCollider = activeCollider
                parkedCount++
            }
        }
    }

    private fun removeEntryAt(index: Int, entry: Entry) {
        if (entry.parkedCollider != null) parkedCount--
        tracked.remove(entry.entity)
        val last = entries.lastIndex
        if (index != last) entries[index] = entries[last]
        entries.removeAt(last)
    }

    private class Entry(
        val entity: Entity,
        var parkedCollider: MeshCollider? = null,
        var initialized: Boolean = false,
    )
}
