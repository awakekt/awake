/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.fsm

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.btree.Blackboard
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World

/**
 * ECS system that drives [AiStateMachineComponent]s each frame.
 */
class AiStateMachineSystem : System {
    private val reusableContext = AiContext(
        world = World(),
        entity = Entity(0L),
        delta = 0f,
        blackboard = Blackboard(),
    )

    /**
     * Updates all enabled [AiStateMachineComponent] entities in [world] by [delta] seconds.
     */
    override fun update(world: World, delta: Float) {
        world.family<AiStateMachineComponent>().forEach { entity, fsmComp ->
            if (!fsmComp.enabled) return@forEach

            reusableContext.update(world, entity, delta, fsmComp.blackboard)
            fsmComp.stateMachine.update(reusableContext)
        }
    }
}
