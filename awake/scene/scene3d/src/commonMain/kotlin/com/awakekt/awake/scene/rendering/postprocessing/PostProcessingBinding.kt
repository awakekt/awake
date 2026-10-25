/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.postprocessing

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.effects.BloomEffect
import com.awakekt.awake.render.effects.CustomPostEffect
import com.awakekt.awake.render.effects.FxaaEffect
import com.awakekt.awake.render.effects.PostProcessingEffect
import com.awakekt.awake.render.effects.ToneMappingGradeEffect
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * Two-way binding between [ScenePostProcessing] schema descriptors and ECS [PostProcessing] components.
 */
object PostProcessingBinding : SceneComponentBinding<PostProcessing, ScenePostProcessing> {
    override val componentClass: KClass<PostProcessing> = PostProcessing::class
    override val schemaClass: KClass<ScenePostProcessing> = ScenePostProcessing::class
    override val serializer = ScenePostProcessing.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: ScenePostProcessing,
        context: SceneResolutionContext,
    ) {
        val runtimeEffects = component.effects.map(::toRuntimeEffect)
        world.add(
            entity,
            PostProcessing(
                enabled = component.enabled,
                effects = runtimeEffects,
            ),
        )
    }

    override fun export(world: World, entity: Entity, component: PostProcessing): ScenePostProcessing {
        val schemaEffects = component.effects.map(::toSchemaEffect)
        return ScenePostProcessing(
            enabled = component.enabled,
            effects = schemaEffects,
        )
    }

    private fun toRuntimeEffect(schema: ScenePostEffect): PostProcessingEffect = when (schema) {
        is SceneToneMappingGradeEffect -> ToneMappingGradeEffect(
            enabled = schema.enabled,
            exposure = schema.exposure,
            contrast = schema.contrast,
            saturation = schema.saturation,
            whiteBalanceTemperature = schema.whiteBalanceTemperature,
            whiteBalanceTint = schema.whiteBalanceTint,
            lift = schema.lift.toFloatArray(),
            gamma = schema.gamma.toFloatArray(),
            gain = schema.gain.toFloatArray(),
            mode = schema.mode,
        )
        is SceneBloomEffect -> BloomEffect(
            enabled = schema.enabled,
            intensity = schema.intensity,
            threshold = schema.threshold,
            knee = schema.knee,
            radius = schema.radius,
        )
        is SceneFxaaEffect -> FxaaEffect(
            enabled = schema.enabled,
            quality = schema.quality,
        )
        is SceneCustomPostEffect -> CustomPostEffect(
            enabled = schema.enabled,
            shaderId = schema.shaderId,
            uniforms = schema.parameters.toFloatArray(),
        )
    }

    private fun toSchemaEffect(runtime: PostProcessingEffect): ScenePostEffect = when (runtime) {
        is ToneMappingGradeEffect -> SceneToneMappingGradeEffect(
            enabled = runtime.enabled,
            exposure = runtime.exposure,
            contrast = runtime.contrast,
            saturation = runtime.saturation,
            whiteBalanceTemperature = runtime.whiteBalanceTemperature,
            whiteBalanceTint = runtime.whiteBalanceTint,
            lift = runtime.lift.toList(),
            gamma = runtime.gamma.toList(),
            gain = runtime.gain.toList(),
            mode = runtime.mode,
        )
        is BloomEffect -> SceneBloomEffect(
            enabled = runtime.enabled,
            intensity = runtime.intensity,
            threshold = runtime.threshold,
            knee = runtime.knee,
            radius = runtime.radius,
        )
        is FxaaEffect -> SceneFxaaEffect(
            enabled = runtime.enabled,
            quality = runtime.quality,
        )
        is CustomPostEffect -> SceneCustomPostEffect(
            enabled = runtime.enabled,
            shaderId = runtime.shaderId,
            parameters = runtime.uniforms.toList(),
        )
    }
}
