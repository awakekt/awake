/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.effects.BloomEffect
import com.awakekt.awake.render.effects.FxaaEffect
import com.awakekt.awake.render.effects.ToneMappingGradeEffect
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessing
import com.awakekt.awake.scene.rendering.postprocessing.PostProcessingBinding
import com.awakekt.awake.scene.rendering.postprocessing.SceneBloomEffect
import com.awakekt.awake.scene.rendering.postprocessing.SceneFxaaEffect
import com.awakekt.awake.scene.rendering.postprocessing.ScenePostProcessing
import com.awakekt.awake.scene.rendering.postprocessing.SceneToneMappingGradeEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostProcessingBindingTest {

    private class EmptyResolutionContext(override val world: World) : SceneResolutionContext {
        override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit
        override fun recordRequest(request: Any) = Unit
    }

    @Test
    fun attachTypedAttachesPostProcessingComponentWithConvertedEffects() {
        val world = World()
        val entity = world.create()
        val schema = ScenePostProcessing(
            enabled = true,
            effects = listOf(
                SceneToneMappingGradeEffect(
                    exposure = 1.5f,
                    contrast = 1.1f,
                    saturation = 1.2f,
                    whiteBalanceTemperature = 10f,
                    whiteBalanceTint = -5f,
                ),
                SceneBloomEffect(
                    intensity = 0.8f,
                    threshold = 1.2f,
                    knee = 0.4f,
                    radius = 12f,
                ),
                SceneFxaaEffect(
                    quality = EffectQuality.High,
                ),
            ),
        )

        PostProcessingBinding.attachTyped(world, entity, schema, EmptyResolutionContext(world))

        val component = assertNotNull(world.get<PostProcessing>(entity))
        assertTrue(component.enabled)
        assertEquals(3, component.effects.size)

        val tm = component.effects[0] as ToneMappingGradeEffect
        assertEquals(1.5f, tm.exposure)
        assertEquals(1.1f, tm.contrast)
        assertEquals(1.2f, tm.saturation)
        assertEquals(10f, tm.whiteBalanceTemperature)
        assertEquals(-5f, tm.whiteBalanceTint)

        val bloom = component.effects[1] as BloomEffect
        assertEquals(0.8f, bloom.intensity)
        assertEquals(1.2f, bloom.threshold)
        assertEquals(0.4f, bloom.knee)
        assertEquals(12f, bloom.radius)

        val fxaa = component.effects[2] as FxaaEffect
        assertEquals(EffectQuality.High, fxaa.quality)
    }

    @Test
    fun exportRoundTripsAllEffectsAndParameters() {
        val world = World()
        val entity = world.create()
        val runtime = PostProcessing(
            enabled = true,
            effects = listOf(
                ToneMappingGradeEffect(exposure = 2.0f, contrast = 1.3f),
                BloomEffect(intensity = 0.6f, radius = 10f),
                FxaaEffect(quality = EffectQuality.Medium),
            ),
        )

        val exported = PostProcessingBinding.export(world, entity, runtime)
        assertTrue(exported.enabled)
        assertEquals(3, exported.effects.size)

        val tm = exported.effects[0] as SceneToneMappingGradeEffect
        assertEquals(2.0f, tm.exposure)
        assertEquals(1.3f, tm.contrast)

        val bloom = exported.effects[1] as SceneBloomEffect
        assertEquals(0.6f, bloom.intensity)
        assertEquals(10f, bloom.radius)

        val fxaa = exported.effects[2] as SceneFxaaEffect
        assertEquals(EffectQuality.Medium, fxaa.quality)
    }

    @Test
    fun validationFlagsInvalidParameters() {
        val invalid = ScenePostProcessing(
            effects = listOf(
                SceneToneMappingGradeEffect(exposure = -1f, contrast = -0.5f),
                SceneBloomEffect(intensity = -2f, radius = 0f),
            ),
        )
        val issues = invalid.validate("camera.post_processing")
        assertFalse(issues.isEmpty())
        assertTrue(issues.any { it.message.contains("exposure") })
        assertTrue(issues.any { it.message.contains("contrast") })
        assertTrue(issues.any { it.message.contains("bloom intensity") })
        assertTrue(issues.any { it.message.contains("bloom radius") })
    }
}
