/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.toColor
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
        val mode = when (type.lowercase()) {
            "cubemap" -> Skybox.Mode.Cubemap(assetPath = cubemapPath ?: "", exposure = exposure)
            "solidcolor" -> Skybox.Mode.SolidColor(color = horizon)
            else -> Skybox.Mode.Procedural(horizonColor = horizon, zenithColor = zenith)
        }
        return Skybox(
            enabled = enabled,
            mode = mode,
        )
    }

    fun Skybox.toSceneComponent(): SceneSkybox = when (val m = mode) {
        is Skybox.Mode.Procedural -> SceneSkybox(
            enabled = enabled,
            horizonColor = SceneColor(m.horizonColor.r, m.horizonColor.g, m.horizonColor.b, m.horizonColor.a),
            zenithColor = SceneColor(m.zenithColor.r, m.zenithColor.g, m.zenithColor.b, m.zenithColor.a),
            cubemapPath = null,
            exposure = 1.0f,
            type = "Procedural",
        )
        is Skybox.Mode.Cubemap -> SceneSkybox(
            enabled = enabled,
            horizonColor = SceneColor(DefaultHorizonColor.r, DefaultHorizonColor.g, DefaultHorizonColor.b, DefaultHorizonColor.a),
            zenithColor = SceneColor(DefaultZenithColor.r, DefaultZenithColor.g, DefaultZenithColor.b, DefaultZenithColor.a),
            cubemapPath = m.assetPath,
            exposure = m.exposure,
            type = "Cubemap",
        )
        is Skybox.Mode.SolidColor -> SceneSkybox(
            enabled = enabled,
            horizonColor = SceneColor(m.color.r, m.color.g, m.color.b, m.color.a),
            zenithColor = SceneColor(m.color.r, m.color.g, m.color.b, m.color.a),
            cubemapPath = null,
            exposure = 1.0f,
            type = "SolidColor",
        )
    }
}
