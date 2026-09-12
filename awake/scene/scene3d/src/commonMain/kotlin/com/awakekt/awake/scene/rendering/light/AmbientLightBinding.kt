/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.toColor
import kotlin.reflect.KClass

object AmbientLightBinding : SceneComponentBinding<AmbientLight, SceneAmbientLight> {
    override val componentClass: KClass<AmbientLight> = AmbientLight::class
    override val schemaClass: KClass<SceneAmbientLight> = SceneAmbientLight::class
    override val serializer = SceneAmbientLight.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneAmbientLight,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: AmbientLight): SceneAmbientLight =
        component.toSceneComponent()

    fun SceneAmbientLight.toComponent(): AmbientLight {
        val lightColor = if (colorR != null && colorG != null && colorB != null) {
            Color(colorR, colorG, colorB, 1f)
        } else {
            color.toColor()
        }
        return AmbientLight(
            intensity = intensity,
            color = lightColor,
        )
    }

    fun AmbientLight.toSceneComponent(): SceneAmbientLight = SceneAmbientLight(
        intensity = intensity,
        color = SceneColor(color.r, color.g, color.b, color.a),
    )
}
