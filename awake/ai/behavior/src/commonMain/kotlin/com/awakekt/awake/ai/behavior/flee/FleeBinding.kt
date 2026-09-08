/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior.flee

import com.awakekt.awake.ai.behavior.FleeBehavior
import com.awakekt.awake.ai.behavior.referenceName
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

object FleeBinding : SceneComponentBinding<FleeBehavior, SceneFlee> {
    override val componentClass: KClass<FleeBehavior> = FleeBehavior::class
    override val schemaClass: KClass<SceneFlee> = SceneFlee::class
    override val serializer = SceneFlee.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneFlee,
        context: SceneResolutionContext,
    ) {
        val comp = component.toComponent()
        world.add(entity, comp)
        component.threat?.let { threatName ->
            context.deferNodeLink(threatName) { threatEntity ->
                comp.threat = threatEntity
            }
        }
    }

    override fun export(world: World, entity: Entity, component: FleeBehavior): SceneFlee =
        component.toSceneComponent(world, entity)

    fun SceneFlee.toComponent(): FleeBehavior = FleeBehavior(
        panicRadius = panicRadius,
        safeRadius = safeRadius,
        fleeDistance = fleeDistance,
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )

    fun FleeBehavior.toSceneComponent(world: World, entity: Entity): SceneFlee = SceneFlee(
        threat = world.referenceName(entity, threat, "FleeBehavior.threat"),
        panicRadius = panicRadius,
        safeRadius = safeRadius,
        fleeDistance = fleeDistance,
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )
}
