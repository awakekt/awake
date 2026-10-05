/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.fog

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.document.toColor
import kotlin.reflect.KClass

/**
 * Binding definition connecting [SceneFog] descriptors to ECS [Fog] components.
 */
object FogBinding : SceneComponentBinding<Fog, SceneFog> {
    override val componentClass: KClass<Fog> = Fog::class
    override val schemaClass: KClass<SceneFog> = SceneFog::class
    override val serializer = SceneFog.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneFog,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: Fog): SceneFog =
        component.toSceneComponent()

    /**
     * Converts this [SceneFog] descriptor into a runtime [Fog] component.
     *
     * @return The instantiated [Fog] component.
     */
    fun SceneFog.toComponent(): Fog {
        val fogColor = if (colorR != null && colorG != null && colorB != null) {
            Color(colorR, colorG, colorB, 1f)
        } else {
            color.toColor()
        }
        return Fog(
            enabled = enabled,
            density = density,
            color = fogColor,
        )
    }

    /**
     * Serializes this runtime [Fog] component into a [SceneFog] descriptor.
     *
     * @return The serialized [SceneFog] descriptor.
     */
    fun Fog.toSceneComponent(): SceneFog = SceneFog(
        enabled = enabled,
        density = density,
        color = SceneColor(color.r, color.g, color.b, color.a),
    )
}
