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
import com.awakekt.awake.render.effects.ToneMappingMode
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
                    mode = ToneMappingMode.Passthrough,
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
        assertEquals(ToneMappingMode.Passthrough, tm.mode)

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
                ToneMappingGradeEffect(exposure = 2.0f, contrast = 1.3f, mode = ToneMappingMode.Passthrough),
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
        assertEquals(ToneMappingMode.Passthrough, tm.mode)

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

    /**
     * A scene camera's colour is display-ready, so a PbrNeutral step would encode it twice (#1007).
     * Passthrough grading, FXAA alone and bloom alone are the valid combinations.
     */
    @Test
    fun validationRejectsAToneMappingStepThatWouldEncodeTwice() {
        val doubleEncoded = ScenePostProcessing(effects = listOf(SceneToneMappingGradeEffect(), SceneFxaaEffect()))
        val issues = doubleEncoded.validate("camera.post_processing")
        val issue = issues.single()
        assertEquals("camera.post_processing.effects[0]", issue.path)
        assertTrue("encode the frame twice" in issue.message, issue.message)
        assertTrue("Passthrough" in issue.message, "the message must name the fix: ${issue.message}")

        val valid = listOf(
            ScenePostProcessing(effects = listOf(SceneFxaaEffect())),
            ScenePostProcessing(effects = listOf(SceneBloomEffect(), SceneFxaaEffect())),
            ScenePostProcessing(
                effects = listOf(SceneToneMappingGradeEffect(saturation = 1.2f, mode = ToneMappingMode.Passthrough), SceneFxaaEffect()),
            ),
            ScenePostProcessing(effects = listOf(SceneToneMappingGradeEffect(enabled = false), SceneFxaaEffect())),
        )
        valid.forEach { stack -> assertTrue(stack.validate("camera").isEmpty(), "$stack: ${stack.validate("camera")}") }
    }

    @Test
    fun validationRejectsASecondEnabledToneMappingStep() {
        val twice = ScenePostProcessing(
            effects = listOf(
                SceneToneMappingGradeEffect(mode = ToneMappingMode.Passthrough),
                SceneToneMappingGradeEffect(mode = ToneMappingMode.Passthrough, contrast = 1.2f),
            ),
        )
        val issue = twice.validate("camera.post_processing").single()
        assertEquals("camera.post_processing.effects[1]", issue.path)
        assertTrue("only one tone_mapping_grade" in issue.message, issue.message)
    }

    @Test
    fun theModeRoundTripsThroughJson() {
        val json = kotlinx.serialization.json.Json
        val stack = ScenePostProcessing(effects = listOf(SceneToneMappingGradeEffect(mode = ToneMappingMode.Passthrough)))
        val text = json.encodeToString(ScenePostProcessing.serializer(), stack)
        assertTrue("\"mode\":\"Passthrough\"" in text, text)
        assertEquals(stack, json.decodeFromString(ScenePostProcessing.serializer(), text))
        val legacy = json.decodeFromString(ScenePostProcessing.serializer(), """{"effects":[{"type":"tone_mapping_grade"}]}""")
        assertEquals(ToneMappingMode.PbrNeutral, (legacy.effects.single() as SceneToneMappingGradeEffect).mode)
    }
}
