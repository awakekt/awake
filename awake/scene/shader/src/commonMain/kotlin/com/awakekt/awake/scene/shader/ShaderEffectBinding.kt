/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * A [SceneShaderEffect] on a live entity; [ShaderEffectSystem] draws it.
 *
 * Replace the component to change the effect. One whose settings differ only in
 * [SceneShaderEffect.parameters] or [SceneShaderEffect.enabled] updates the running effect in place;
 * one with another document or other textures replaces it.
 *
 * @property settings The effect as the scene file describes it, kept so the scene exports unchanged.
 */
class ShaderEffectSource(val settings: SceneShaderEffect)

/** Loads [SceneShaderEffect] as the entity's [ShaderEffectSource], and exports it back unchanged. */
object ShaderEffectBinding : SceneComponentBinding<ShaderEffectSource, SceneShaderEffect> {
    override val componentClass: KClass<ShaderEffectSource> = ShaderEffectSource::class
    override val schemaClass: KClass<SceneShaderEffect> = SceneShaderEffect::class
    override val serializer = SceneShaderEffect.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneShaderEffect,
        context: SceneResolutionContext,
    ) {
        world.add(entity, ShaderEffectSource(component))
    }

    override fun export(world: World, entity: Entity, component: ShaderEffectSource): SceneShaderEffect =
        component.settings
}
