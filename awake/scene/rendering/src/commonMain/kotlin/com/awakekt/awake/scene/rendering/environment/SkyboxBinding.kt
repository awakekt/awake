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
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.toColor
import com.awakekt.awake.scene.rendering.Skybox
import kotlin.reflect.KClass

object SkyboxBinding : SceneComponentBinding<Skybox, SceneSkybox> {
    override val componentClass: KClass<Skybox> = Skybox::class
    override val schemaClass: KClass<SceneSkybox> = SceneSkybox::class
    override val serializer = SceneSkybox.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneSkybox,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: Skybox): SceneSkybox =
        component.toSceneComponent()

    fun SceneSkybox.toComponent(): Skybox {
        val horizon = if (horizonColorR != null && horizonColorG != null && horizonColorB != null) {
            Color(horizonColorR, horizonColorG, horizonColorB, 1f)
        } else {
            horizonColor.toColor()
        }
        val zenith = if (zenithColorR != null && zenithColorG != null && zenithColorB != null) {
            Color(zenithColorR, zenithColorG, zenithColorB, 1f)
        } else {
            zenithColor.toColor()
        }
        return Skybox(
            enabled = enabled,
            horizonColor = horizon,
            zenithColor = zenith,
            cubemapPath = cubemapPath,
            exposure = exposure,
            type = runCatching { Skybox.Type.valueOf(type) }.getOrDefault(Skybox.Type.Procedural),
        )
    }

    fun Skybox.toSceneComponent(): SceneSkybox = SceneSkybox(
        enabled = enabled,
        horizonColor = SceneColor(horizonColor.r, horizonColor.g, horizonColor.b, horizonColor.a),
        zenithColor = SceneColor(zenithColor.r, zenithColor.g, zenithColor.b, zenithColor.a),
        cubemapPath = cubemapPath,
        exposure = exposure,
        type = type.name,
    )
}
