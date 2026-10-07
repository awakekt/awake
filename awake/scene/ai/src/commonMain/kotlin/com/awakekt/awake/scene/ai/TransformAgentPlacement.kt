/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.behavior.AgentPlacement
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Places an agent by its scene `Transform`: the wrapper's answer to "where is that entity, and how
 * does it move" for `awake:ai:behavior`, which has no scene of its own.
 *
 * An agent is where its transform's `position` says, and moves by rewriting it. An entity with no
 * `Transform` (destroyed, or never placed) has no place: an agent without one does not move, and a
 * target or threat without one counts as gone.
 */
object TransformAgentPlacement : AgentPlacement {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val transform = world.get<Transform>(entity) ?: return false
        into.set(transform.position)
        return true
    }

    override fun moveBy(world: World, entity: Entity, dx: Float, dz: Float) {
        val transform = world.get<Transform>(entity) ?: return
        transform.position.x += dx
        transform.position.z += dz
    }
}
