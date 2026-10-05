/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.particles

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.EmitterPlacement
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Places an emitter's entity by its scene `Transform`: this is the wrapper's whole answer to "where
 * is that entity" for `awake:particles`, which has no scene of its own.
 *
 * A root node is where its own `position` says; a child is where its world matrix put it as of the
 * last transform pass. An entity with no `Transform` (destroyed, or never placed) has no place, so an
 * emitter following it keeps spawning from wherever it last was instead of failing.
 */
object TransformPlacement : EmitterPlacement {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val transform = world.get<Transform>(entity) ?: return false
        if (transform.parent == null) {
            into.set(transform.position)
        } else {
            val placed = transform.worldMatrix
            into.set(placed.m03, placed.m13, placed.m23)
        }
        return true
    }

    override fun orientation(world: World, entity: Entity): Mat4? = world.get<Transform>(entity)?.worldMatrix
}
