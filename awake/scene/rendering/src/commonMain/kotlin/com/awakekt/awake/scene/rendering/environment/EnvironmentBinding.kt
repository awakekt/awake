/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.environment

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.rendering.Environment
import kotlin.reflect.KClass

object EnvironmentBinding : SceneComponentBinding<Environment, SceneEnvironment> {
    override val componentClass: KClass<Environment> = Environment::class
    override val schemaClass: KClass<SceneEnvironment> = SceneEnvironment::class
    override val serializer = SceneEnvironment.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneEnvironment,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: Environment): SceneEnvironment =
        component.toSceneComponent()

    fun SceneEnvironment.toComponent(): Environment = Environment(
        showEnvironment = showEnvironment,
        horizonColor = Color(horizonColorR, horizonColorG, horizonColorB, 1f),
        zenithColor = Color(zenithColorR, zenithColorG, zenithColorB, 1f),
        fogDensity = fogDensity,
        fogColor = Color(fogColorR, fogColorG, fogColorB, 1f),
        ambientLight = ambientLight,
    )

    fun Environment.toSceneComponent(): SceneEnvironment = SceneEnvironment(
        showEnvironment = showEnvironment,
        horizonColorR = horizonColor.r,
        horizonColorG = horizonColor.g,
        horizonColorB = horizonColor.b,
        zenithColorR = zenithColor.r,
        zenithColorG = zenithColor.g,
        zenithColorB = zenithColor.b,
        fogDensity = fogDensity,
        fogColorR = fogColor.r,
        fogColorG = fogColor.g,
        fogColorB = fogColor.b,
        ambientLight = ambientLight,
    )
}
