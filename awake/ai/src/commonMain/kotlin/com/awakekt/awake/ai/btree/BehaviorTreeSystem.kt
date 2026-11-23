/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.AiEventSink
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family1System
import com.awakekt.awake.ecs.World

/**
 * ECS system that evaluates active [BehaviorTreeComponent]s each frame.
 *
 * @param events Where the trees' emitted events go; dropped by default.
 */
class BehaviorTreeSystem(private val events: AiEventSink = AiEventSink.None) : Family1System<BehaviorTreeComponent>(BehaviorTreeComponent::class) {
    // Reused tick context to ensure zero allocation per frame loop
    private val reusableContext = AiContext(
        world = World(),
        entity = Entity(0L),
        delta = 0f,
        blackboard = Blackboard(),
    )

    override fun process(world: World, delta: Float, entity: Entity, component: BehaviorTreeComponent) {
        if (!component.enabled) return

        reusableContext.update(world, entity, delta, component.blackboard)
        reusableContext.events = events
        component.activeStatus = component.tree.tick(reusableContext)
    }

    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, component ->
        process(world, delta, entity, component)
    }
}
