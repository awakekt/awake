/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostProcessingStackTest {

    private val targetContext = ImagePassTargetContext(
        width = 1920,
        height = 1080,
        clipSpace = ClipSpace.Vulkan,
        effectQuality = EffectQuality.High,
    )

    @Test
    fun buildChainEmptyWhenDisabledOrOffQuality() {
        val offContext = targetContext.copy(effectQuality = EffectQuality.Off)
        val chainOff = PostProcessingStack.buildChain(
            listOf(ToneMappingGradeEffect(), BloomEffect(), FxaaEffect()),
            offContext,
        )
        assertTrue(chainOff.isEmpty)

        val disabledEffects = listOf(
            ToneMappingGradeEffect(enabled = false),
            BloomEffect(enabled = false),
            FxaaEffect(enabled = false),
        )
        val chainDisabled = PostProcessingStack.buildChain(disabledEffects, targetContext)
        assertTrue(chainDisabled.isEmpty)

        val zeroSizeContext = targetContext.copy(width = 0, height = 0)
        val chainZero = PostProcessingStack.buildChain(listOf(ToneMappingGradeEffect()), zeroSizeContext)
        assertTrue(chainZero.isEmpty)
    }

    @Test
    fun buildChainToneMappingOnlyWritesDirectlyToActiveTarget() {
        val chain = PostProcessingStack.buildChain(
            listOf(ToneMappingGradeEffect(exposure = 1.2f, contrast = 1.1f)),
            targetContext,
        )
        assertFalse(chain.isEmpty)
        assertEquals(0, chain.transientTargets.size)
        assertEquals(1, chain.steps.size)

        val step = chain.steps[0]
        assertEquals(PostProcessingShaders.TONE_MAPPING_GRADE_SHADER_ID, step.shader.id)
        assertTrue(step.destination is ImagePassDestination.ActiveTarget)
        assertEquals(1, step.inputs.size)
        assertEquals(ImagePassSource.DestinationSnapshot, step.inputs[0].source)
    }

    @Test
    fun buildChainBloomAndToneMappingReusesBlurChainAndCompositesInSinglePass() {
        val chain = PostProcessingStack.buildChain(
            listOf(
                ToneMappingGradeEffect(exposure = 1.0f),
                BloomEffect(intensity = 0.8f, threshold = 1.2f, knee = 0.5f, radius = 16f),
            ),
            targetContext,
        )
        assertFalse(chain.isEmpty)
        assertTrue(chain.transientTargets.isNotEmpty())

        // Transients must include bright pass and blur levels
        assertTrue(chain.transientTargets.any { it.id == PostProcessingStack.TRANSIENT_BLOOM_BRIGHT_ID })

        // First step is bright pass
        val brightStep = chain.steps.first()
        assertEquals(PostProcessingShaders.BLOOM_BRIGHT_PASS_SHADER_ID, brightStep.shader.id)
        assertEquals(ImagePassSource.DestinationSnapshot, brightStep.inputs[0].source)
        assertTrue(brightStep.destination is ImagePassDestination.TransientTarget)

        // Last step is combined tone-mapping + bloom
        val lastStep = chain.steps.last()
        assertEquals(PostProcessingShaders.TONE_MAPPING_GRADE_BLOOM_SHADER_ID, lastStep.shader.id)
        assertTrue(lastStep.destination is ImagePassDestination.ActiveTarget)
        assertEquals(2, lastStep.inputs.size)
        assertEquals(ImagePassSource.DestinationSnapshot, lastStep.inputs[0].source)
        assertTrue(lastStep.inputs[1].source is ImagePassSource.TransientTarget)
    }

    @Test
    fun buildChainBloomToneMappingAndFxaaPipelinesToLdrTarget() {
        val chain = PostProcessingStack.buildChain(
            listOf(
                ToneMappingGradeEffect(exposure = 1.0f),
                BloomEffect(intensity = 0.5f, radius = 8f),
                FxaaEffect(quality = EffectQuality.High),
            ),
            targetContext,
        )
        assertFalse(chain.isEmpty)

        // LDR intermediate target is allocated for FXAA input
        assertTrue(chain.transientTargets.any { it.id == PostProcessingStack.TRANSIENT_LDR_SCENE_ID })

        // Tone mapping outputs to TRANSIENT_LDR_SCENE_ID
        val toneMapStep = chain.steps.first { it.shader.id == PostProcessingShaders.TONE_MAPPING_GRADE_BLOOM_SHADER_ID }
        assertTrue(toneMapStep.destination is ImagePassDestination.TransientTarget)
        assertEquals(
            PostProcessingStack.TRANSIENT_LDR_SCENE_ID,
            (toneMapStep.destination as ImagePassDestination.TransientTarget).id,
        )

        // FXAA is final step reading TRANSIENT_LDR_SCENE_ID and writing to ActiveTarget
        val fxaaStep = chain.steps.last()
        assertEquals(PostProcessingShaders.FXAA_SHADER_ID, fxaaStep.shader.id)
        assertTrue(fxaaStep.destination is ImagePassDestination.ActiveTarget)
        val fxaaInput = fxaaStep.inputs[0].source as ImagePassSource.TransientTarget
        assertEquals(PostProcessingStack.TRANSIENT_LDR_SCENE_ID, fxaaInput.id)
    }

    @Test
    fun evaluateBloomBrightPassReferenceSoftKnee() {
        // Below threshold - knee: completely zero
        val below = PostProcessingStack.evaluateBloomBrightPassReference(
            radiance = floatArrayOf(0.4f, 0.4f, 0.4f),
            threshold = 1.0f,
            knee = 0.5f,
        )
        assertEquals(0f, below[0])
        assertEquals(0f, below[1])
        assertEquals(0f, below[2])

        // Well above threshold + knee: linear roll-off
        val high = PostProcessingStack.evaluateBloomBrightPassReference(
            radiance = floatArrayOf(3.0f, 3.0f, 3.0f),
            threshold = 1.0f,
            knee = 0.5f,
        )
        assertTrue(high[0] > 1.5f)
        assertTrue(high[1] > 1.5f)
        assertTrue(high[2] > 1.5f)
    }

    @Test
    fun evaluateToneMappingGradeReferenceIdentity() {
        // Shading a moderate value with identity parameters produces valid output in [0, 1]
        val result = PostProcessingStack.evaluateToneMappingGradeReference(
            radiance = floatArrayOf(0.5f, 0.5f, 0.5f),
            bloom = floatArrayOf(0f, 0f, 0f),
            exposure = 1f,
            contrast = 1f,
            saturation = 1f,
            bloomIntensity = 0f,
            whiteBalanceTemperature = 0f,
            whiteBalanceTint = 0f,
            lift = floatArrayOf(0f, 0f, 0f),
            gamma = floatArrayOf(1f, 1f, 1f),
            gain = floatArrayOf(1f, 1f, 1f),
        )
        assertTrue(result[0] in 0.0f..1.0f)
        assertTrue(result[1] in 0.0f..1.0f)
        assertTrue(result[2] in 0.0f..1.0f)
        // Alpha contains perceptual luma
        assertTrue(result[3] > 0.0f)
    }
}
