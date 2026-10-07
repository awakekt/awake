/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/** Where a test entity is: a stand-in for whatever places entities in an app, which the behaviours do not know. */
internal class Placed(val position: Vec3f = Vec3f(0f, 0f, 0f))

/** Places and moves an entity by its [Placed] component, so these tests need no scene. */
internal object PlacedAgents : AgentPlacement {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
        val placed = world.get<Placed>(entity) ?: return false
        into.set(placed.position)
        return true
    }

    override fun moveBy(world: World, entity: Entity, dx: Float, dz: Float) {
        val placed = world.get<Placed>(entity) ?: return
        placed.position.x += dx
        placed.position.z += dz
    }
}
