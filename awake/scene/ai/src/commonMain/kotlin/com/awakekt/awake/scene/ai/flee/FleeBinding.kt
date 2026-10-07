/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.flee

import com.awakekt.awake.ai.behavior.FleeBehavior
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.ai.ensurePathRequest
import com.awakekt.awake.scene.ai.referenceName
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * Scene component binding for [FleeBehavior] and [SceneFlee].
 */
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
        world.ensurePathRequest(entity)
        component.threat?.let { threatName ->
            context.deferNodeLink(threatName) { threatEntity ->
                comp.threat = threatEntity
            }
        }
    }

    override fun export(world: World, entity: Entity, component: FleeBehavior): SceneFlee =
        component.toSceneComponent(world, entity)

    /**
     * Converts a [SceneFlee] descriptor to a runtime [FleeBehavior] component.
     *
     * @return Runtime [FleeBehavior] instance.
     */
    fun SceneFlee.toComponent(): FleeBehavior = FleeBehavior(
        panicRadius = panicRadius,
        safeRadius = safeRadius,
        fleeDistance = fleeDistance,
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )

    /**
     * Converts a runtime [FleeBehavior] component to a serializable [SceneFlee] descriptor.
     *
     * @param world Active ECS world used to resolve threat entity names.
     * @param entity Entity owning this flee component.
     * @return Serializable [SceneFlee] instance.
     */
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
