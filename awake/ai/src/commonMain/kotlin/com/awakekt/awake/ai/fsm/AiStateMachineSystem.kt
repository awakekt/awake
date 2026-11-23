/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.fsm

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.AiEventSink
import com.awakekt.awake.ai.btree.Blackboard
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1System
import com.awakekt.awake.ecs.World

/**
 * ECS system that drives [AiStateMachineComponent]s each frame.
 *
 * @param events Where the machines' emitted events go; dropped by default.
 */
class AiStateMachineSystem(private val events: AiEventSink = AiEventSink.None) : Family1System<AiStateMachineComponent>(AiStateMachineComponent::class) {
    private val reusableContext = AiContext(
        world = World(),
        entity = Entity(0L),
        delta = 0f,
        blackboard = Blackboard(),
    )

    override fun process(world: World, delta: Float, entity: Entity, component: AiStateMachineComponent) {
        if (!component.enabled) return

        reusableContext.update(world, entity, delta, component.blackboard)
        reusableContext.events = events
        component.stateMachine.update(reusableContext)
    }

    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, component ->
        process(world, delta, entity, component)
    }
}
