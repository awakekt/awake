/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.tonemapping

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

object ToneMappingBinding : SceneComponentBinding<ToneMapping, SceneToneMapping> {
    override val componentClass: KClass<ToneMapping> = ToneMapping::class
    override val schemaClass: KClass<SceneToneMapping> = SceneToneMapping::class
    override val serializer = SceneToneMapping.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneToneMapping,
        context: SceneResolutionContext,
    ) {
        world.add(entity, ToneMapping(exposure = component.exposure))
    }

    override fun export(world: World, entity: Entity, component: ToneMapping): SceneToneMapping =
        SceneToneMapping(exposure = component.exposure)
}
