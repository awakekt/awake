/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

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
