/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.chase

import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.ai.ensurePathRequest
import com.awakekt.awake.scene.ai.referenceName
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * Scene component binding for [ChaseBehavior] and [SceneChase].
 */
object ChaseBinding : SceneComponentBinding<ChaseBehavior, SceneChase> {
    override val componentClass: KClass<ChaseBehavior> = ChaseBehavior::class
    override val schemaClass: KClass<SceneChase> = SceneChase::class
    override val serializer = SceneChase.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneChase,
        context: SceneResolutionContext,
    ) {
        val comp = component.toComponent()
        world.add(entity, comp)
        world.ensurePathRequest(entity)
        component.target?.let { targetName ->
            context.deferNodeLink(targetName) { targetEntity ->
                comp.target = targetEntity
            }
        }
    }

    override fun export(world: World, entity: Entity, component: ChaseBehavior): SceneChase =
        component.toSceneComponent(world, entity)

    /**
     * Converts a [SceneChase] descriptor to a runtime [ChaseBehavior] component.
     *
     * @return Runtime [ChaseBehavior] instance.
     */
    fun SceneChase.toComponent(): ChaseBehavior = ChaseBehavior(
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )

    /**
     * Converts a runtime [ChaseBehavior] component to a serializable [SceneChase] descriptor.
     *
     * @param world Active ECS world used to resolve target entity names.
     * @param entity Entity owning this chase component.
     * @return Serializable [SceneChase] instance.
     */
    fun ChaseBehavior.toSceneComponent(world: World, entity: Entity): SceneChase = SceneChase(
        target = world.referenceName(entity, target, "ChaseBehavior.target"),
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )
}
