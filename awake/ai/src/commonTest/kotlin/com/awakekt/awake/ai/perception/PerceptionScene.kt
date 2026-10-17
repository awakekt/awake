/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/** A made-up scene: positions and facings kept in maps, as a game's own components would. */
internal class PerceptionScene : PerceptionSpace {
    val world = World()
    private val positions = HashMap<Entity, Vec3f>()
    private val facings = HashMap<Entity, Vec3f>()

    fun place(entity: Entity, x: Float, z: Float, faceX: Float? = null, faceZ: Float? = null): Entity {
        positions[entity] = Vec3f(x, 0f, z)
        if (faceX != null && faceZ != null) facings[entity] = Vec3f(faceX, 0f, faceZ)
        return entity
    }

    fun spawn(x: Float, z: Float, faceX: Float? = null, faceZ: Float? = null): Entity = place(world.create(), x, z, faceX, faceZ)

    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val at = positions[entity] ?: return false
        into.set(at)
        return true
    }

    override fun forward(world: World, entity: Entity, into: Vec3f): Boolean {
        val facing = facings[entity] ?: return false
        into.set(facing)
        return true
    }
}

/** A wall along x = [x] from z = [fromZ] to z = [toZ], which blocks any line crossing it. */
internal class Wall(private val x: Float, private val fromZ: Float, private val toZ: Float) : LineOfSight {
    override fun isClear(from: Vec3f, to: Vec3f, viewer: Entity, target: Entity?): Boolean {
        if ((from.x - x) * (to.x - x) > 0f || from.x == to.x) return true
        val t = (x - from.x) / (to.x - from.x)
        val z = from.z + (to.z - from.z) * t
        return z !in fromZ..toZ
    }
}
