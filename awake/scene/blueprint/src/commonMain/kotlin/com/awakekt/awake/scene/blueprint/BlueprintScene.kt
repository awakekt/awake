/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.EntityNames
import com.awakekt.awake.scene.physics.PhysicsSystem

/**
 * What nodes that act on the scene reach through. [BlueprintSystem] owns one; pass it to a game node
 * that needs the world.
 */
class BlueprintScene internal constructor(private val physics: PhysicsSystem?) {
    /** The world [BlueprintSystem] runs in, set before any node runs. */
    lateinit var world: World
        private set

    /** Entity lookup by name in [world]. */
    lateinit var names: EntityNames
        private set

    /**
     * Destroys [entity], freeing its physics body first so the body cannot outlive it.
     *
     * @param entity The entity to destroy.
     */
    fun destroy(entity: Entity) {
        physics?.destroyBody(world, entity)
        world.destroy(entity)
    }

    /** Whether [world] is new to this scene. */
    internal fun bind(world: World): Boolean {
        if (::world.isInitialized && this.world === world) return false
        this.world = world
        names = EntityNames(world)
        return true
    }
}
