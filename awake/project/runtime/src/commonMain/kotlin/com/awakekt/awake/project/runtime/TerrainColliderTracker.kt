/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.EntitySet
import com.awakekt.awake.ecs.Family1
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent

/**
 * Tracks static [TerrainComponent] colliders for [StaticColliderStreamSystem], parking distant
 * heightfield colliders via `collider = false` while leaving `TerrainComponent.heightmap` intact.
 */
internal class TerrainColliderTracker(
    private val physics: PhysicsSystem,
    config: ColliderStreamConfig,
    private val originScratch: Vec3f,
) {
    private val reachSq = config.terrainReach * config.terrainReach
    private val leaveSq = config.terrainLeave * config.terrainLeave
    private val entries = ArrayList<Entry>()
    private val tracked = EntitySet()
    var parkedCount: Int = 0
        private set

    val trackedCount: Int get() = entries.size

    fun discover(family: Family1<TerrainComponent>) {
        family.forEach { entity, terrain ->
            if (terrain.collider && entity !in tracked) {
                tracked.add(entity)
                entries.add(Entry(entity))
            }
        }
    }

    fun evaluate(world: World, startIndex: Int, obsX: Float, obsZ: Float, transformTypeId: ComponentTypeId) {
        var index = entries.lastIndex
        while (index >= startIndex) {
            val entry = entries[index]
            val entity = entry.entity
            val terrain = if (world.isAlive(entity)) world.get<TerrainComponent>(entity) else null
            if (terrain == null || (!entry.parked && !terrain.collider)) {
                removeEntryAt(index, entry)
            } else {
                stepEntry(world, entry, terrain, obsX, obsZ, transformTypeId)
            }
            index--
        }
    }

    fun prune(world: World, limit: Int) {
        var index = minOf(limit, entries.size) - 1
        while (index >= 0) {
            val entry = entries[index]
            val terrain = if (world.isAlive(entry.entity)) world.get<TerrainComponent>(entry.entity) else null
            if (terrain == null || (!entry.parked && !terrain.collider)) {
                removeEntryAt(index, entry)
            }
            index--
        }
    }

    fun clear() {
        entries.clear()
        tracked.clear()
        parkedCount = 0
    }

    @Suppress("LongParameterList") // Avoids allocating a context object per terrain evaluation.
    private fun stepEntry(
        world: World,
        entry: Entry,
        terrain: TerrainComponent,
        obsX: Float,
        obsZ: Float,
        transformTypeId: ComponentTypeId,
    ) {
        val entity = entry.entity
        val origin = resolveStaticOrigin(world, entity, transformTypeId, originScratch)
        val heightmap = terrain.heightmap
        val minX = origin.x + heightmap.minX
        val maxX = minX + heightmap.halfExtentX * 2f
        val minZ = origin.z + heightmap.minZ
        val maxZ = minZ + heightmap.halfExtentZ * 2f
        val dx = if (obsX < minX) {
            minX - obsX
        } else if (obsX > maxX) {
            obsX - maxX
        } else {
            0f
        }
        val dz = if (obsZ < minZ) {
            minZ - obsZ
        } else if (obsZ > maxZ) {
            obsZ - maxZ
        } else {
            0f
        }
        val distSq = dx * dx + dz * dz
        if (entry.parked) {
            if (distSq <= reachSq) {
                if (!terrain.collider) world.add(entity, terrain.copy(collider = true))
                entry.parked = false
                entry.initialized = true
                parkedCount--
            } else if (terrain.collider) {
                physics.destroyBody(world, entity)
                world.remove<PhysicsBody>(entity)
                world.add(entity, terrain.copy(collider = false))
            }
        } else {
            val maxDistSq = if (entry.initialized || world.has<PhysicsBody>(entity)) leaveSq else reachSq
            entry.initialized = true
            if (distSq > maxDistSq) {
                physics.destroyBody(world, entity)
                world.remove<PhysicsBody>(entity)
                world.add(entity, terrain.copy(collider = false))
                entry.parked = true
                parkedCount++
            }
        }
    }

    private fun removeEntryAt(index: Int, entry: Entry) {
        if (entry.parked) parkedCount--
        tracked.remove(entry.entity)
        val last = entries.lastIndex
        if (index != last) entries[index] = entries[last]
        entries.removeAt(last)
    }

    private class Entry(
        val entity: Entity,
        var parked: Boolean = false,
        var initialized: Boolean = false,
    )
}
