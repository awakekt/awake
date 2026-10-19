/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LayerBlurEffectTest {

    @Test
    fun buildChainGeneratesCorrectDownsampleAndUpsampleSteps() {
        val blur = assertNotNull(BlurChain.forRadius(16f, EffectQuality.High, targetOutputLevel = 0))
        val chain = LayerBlurEffect.buildChain(
            source = ImagePassSource.TransientTarget("source_layer"),
            blur = blur,
            width = 64,
            height = 64,
        )

        assertEquals(blur.iterations, chain.transientTargets.size)
        // iterations downsample steps + iterations upsample steps = 2 * iterations
        assertEquals(blur.iterations * 2, chain.steps.size)

        // First step reads from source
        val firstStep = chain.steps.first()
        assertEquals(ImagePassSource.TransientTarget("source_layer"), firstStep.inputs.first().source)
        assertEquals(ImagePassDestination.TransientTarget(LayerBlurEffect.levelTargetId(1)), firstStep.destination)

        // Last step writes to ActiveTarget
        val lastStep = chain.steps.last()
        assertEquals(ImagePassSource.TransientTarget(LayerBlurEffect.levelTargetId(1)), lastStep.inputs.first().source)
        val dst = assertIs<ImagePassDestination.ActiveTarget>(lastStep.destination)
        assertTrue(dst.isPremultiplied)
        assertEquals(false, dst.blendEnabled)
    }

    @Test
    fun cpuReferenceTwinBlursDownToLevelZero() {
        val width = 32
        val height = 32
        val pixels = FloatArray(width * height * 4) { 0f }
        // Center 4x4 opaque white square
        for (y in 14..17) {
            for (x in 14..17) {
                val idx = (y * width + x) * 4
                pixels[idx] = 1f
                pixels[idx + 1] = 1f
                pixels[idx + 2] = 1f
                pixels[idx + 3] = 1f
            }
        }

        val blur = BlurChain(iterations = 2, offset = 1f, outputLevel = 0)
        val region = BackdropRegion(0, 0, width, height)
        val blurred = BackdropBlurReference.blur(pixels, width, height, region, blur)

        val out = FloatArray(4)
        blurred.sample(15, 15, out)
        // Center should remain bright but smoothed
        assertTrue(out[0] > 0.03f && out[3] > 0.03f, "Center should have positive color and alpha")
        // Adjacent pixel should receive blurred light
        blurred.sample(12, 12, out)
        assertTrue(out[0] > 0.01f && out[3] > 0.01f, "Adjacent pixel should receive blurred light")
    }
}
