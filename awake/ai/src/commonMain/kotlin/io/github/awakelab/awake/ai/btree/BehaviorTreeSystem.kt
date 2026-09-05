/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.btree

import io.github.awakelab.awake.ai.AiContext
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World

/**
 * ECS system that evaluates active [BehaviorTreeComponent]s each frame.
 */
class BehaviorTreeSystem : System {
    // Reused tick context to ensure zero allocation per frame loop
    private val reusableContext = AiContext(
        world = World(),
        entity = Entity(0L),
        delta = 0f,
        blackboard = Blackboard(),
    )

    /**
     * Ticks all enabled [BehaviorTreeComponent] entities in [world] by [delta] seconds.
     */
    override fun update(world: World, delta: Float) {
        world.family<BehaviorTreeComponent>().forEach { entity, btc ->
            if (!btc.enabled) return@forEach

            reusableContext.update(world, entity, delta, btc.blackboard)
            btc.activeStatus = btc.root.tick(reusableContext)
        }
    }
}
