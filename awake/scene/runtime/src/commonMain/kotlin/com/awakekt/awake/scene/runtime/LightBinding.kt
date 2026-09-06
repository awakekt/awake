/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneComponentBinding
import com.awakekt.awake.scene.document.SceneLight
import com.awakekt.awake.scene.document.SceneResolutionContext
import com.awakekt.awake.scene.document.toSceneVec3
import com.awakekt.awake.scene.document.toVec3
import com.awakekt.awake.scene.rendering.Light

object LightBinding : SceneComponentBinding<Light, SceneLight> {
    override val componentClass = Light::class

    override fun canResolve(component: SceneComponent): Boolean = component is SceneLight

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val light = component as SceneLight
        world.add(entity, light.toComponent())
    }

    override fun export(world: World, entity: Entity, component: Light): SceneLight =
        component.toSceneComponent()

    override fun exportFrom(world: World, entity: Entity): SceneLight? =
        world.get(entity, componentClass)?.let { export(world, entity, it) }

    fun SceneLight.toComponent(): Light = Light(
        color = color.toVec3(),
        intensity = intensity,
        direction = direction.toVec3(),
        type = when (type) {
            SceneLight.Type.Directional -> Light.Type.Directional
            SceneLight.Type.Point -> Light.Type.Point
        },
        range = range,
    )

    fun Light.toSceneComponent(): SceneLight = SceneLight(
        color = color.toSceneVec3(),
        intensity = intensity,
        direction = direction.toSceneVec3(),
        type = when (type) {
            Light.Type.Directional -> SceneLight.Type.Directional
            Light.Type.Point -> SceneLight.Type.Point
        },
        range = range,
    )
}
