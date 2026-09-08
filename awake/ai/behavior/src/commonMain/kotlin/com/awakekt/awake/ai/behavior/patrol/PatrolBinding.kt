/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior.patrol

import com.awakekt.awake.ai.behavior.PatrolBehavior
import com.awakekt.awake.ai.behavior.PatrolStyle
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneVec3
import kotlin.reflect.KClass

object PatrolBinding : SceneComponentBinding<PatrolBehavior, ScenePatrol> {
    override val componentClass: KClass<PatrolBehavior> = PatrolBehavior::class
    override val schemaClass: KClass<ScenePatrol> = ScenePatrol::class
    override val serializer = ScenePatrol.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: ScenePatrol,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: PatrolBehavior): ScenePatrol =
        component.toSceneComponent()

    fun ScenePatrol.toComponent(): PatrolBehavior = PatrolBehavior(
        stops = stops.map { Vec3f(it.x, it.y, it.z) },
        style = when (style) {
            ScenePatrol.Style.Loop -> PatrolStyle.Loop
            ScenePatrol.Style.PingPong -> PatrolStyle.PingPong
            ScenePatrol.Style.Once -> PatrolStyle.Once
        },
        dwellSeconds = dwellSeconds,
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )

    fun PatrolBehavior.toSceneComponent(): ScenePatrol = ScenePatrol(
        stops = stops.map { SceneVec3(it.x, it.y, it.z) },
        style = when (style) {
            PatrolStyle.Loop -> ScenePatrol.Style.Loop
            PatrolStyle.PingPong -> ScenePatrol.Style.PingPong
            PatrolStyle.Once -> ScenePatrol.Style.Once
        },
        dwellSeconds = dwellSeconds,
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )
}
