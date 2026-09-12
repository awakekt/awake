/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.rendering.Light
import com.awakekt.awake.scene.rendering.toSceneVec3
import com.awakekt.awake.scene.rendering.toVec3
import kotlin.reflect.KClass

object LightBinding : SceneComponentBinding<Light, SceneLight> {
    override val componentClass: KClass<Light> = Light::class
    override val schemaClass: KClass<SceneLight> = SceneLight::class
    override val serializer = SceneLight.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneLight,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: Light): SceneLight =
        component.toSceneComponent()

    fun SceneLight.toComponent(): Light = Light(
        color = Vec3f(color.r, color.g, color.b),
        intensity = intensity,
        direction = direction.toVec3(),
        type = when (type) {
            SceneLight.Type.Directional -> Light.Type.Directional
            SceneLight.Type.Point -> Light.Type.Point
        },
        range = range,
        shadowsEnabled = shadowsEnabled,
    )

    fun Light.toSceneComponent(): SceneLight = SceneLight(
        color = SceneColor(color.x, color.y, color.z, 1f),
        intensity = intensity,
        direction = direction.toSceneVec3(),
        type = when (type) {
            Light.Type.Directional -> SceneLight.Type.Directional
            Light.Type.Point -> SceneLight.Type.Point
        },
        range = range,
        shadowsEnabled = shadowsEnabled,
    )
}
