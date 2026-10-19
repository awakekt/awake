/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassTargetContext
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlurChainTest {

    @Test
    fun blurChainInvariantsAndPassCounts() {
        val blur = BlurChain(iterations = 4, offset = 1.5f, outputLevel = 2)
        assertEquals(4, blur.iterations)
        assertEquals(1.5f, blur.offset)
        assertEquals(2, blur.outputLevel)
        assertEquals(2, blur.upPasses)
        assertEquals(6, blur.passCount)
        assertTrue(blur.sigma > 0f)
        assertTrue(blur.extent > 0)
    }

    @Test
    fun forRadiusReturnsNullForOffQualityOrZeroRadius() {
        assertNull(BlurChain.forRadius(16f, EffectQuality.Off))
        assertNull(BlurChain.forRadius(0f, EffectQuality.High))
        assertNull(BlurChain.forRadius(-5f, EffectQuality.High))
    }

    @Test
    fun forRadiusResolvesOptimalSigmaAcrossRadii() {
        val testRadii = listOf(4f, 8f, 16f, 32f, 64f)
        for (radius in testRadii) {
            val blur = BlurChain.forRadius(radius, EffectQuality.High)
            assertNotNull(blur, "BlurChain should not be null for radius $radius")
            assertTrue(
                blur.iterations <= 6,
                "High quality must not exceed 6 iterations, got ${blur.iterations}",
            )
            // Sigma should be within reasonable tolerance of target radius
            val relativeDiff = abs(blur.sigma - radius) / radius
            assertTrue(
                relativeDiff < 0.25f,
                "Resolved sigma ${blur.sigma} differed by $relativeDiff from target $radius",
            )
        }
    }

    @Test
    fun levelScaleAndSizeMath() {
        assertEquals(1f, BlurChain.levelScale(0))
        assertEquals(2f, BlurChain.levelScale(1))
        assertEquals(4f, BlurChain.levelScale(2))
        assertEquals(8f, BlurChain.levelScale(3))

        assertEquals(100, BlurChain.levelSize(100, 0))
        assertEquals(50, BlurChain.levelSize(100, 1))
        assertEquals(25, BlurChain.levelSize(100, 2))
        assertEquals(51, BlurChain.levelSize(101, 1))
    }

    @Test
    fun backdropRegionScalingAndAround() {
        val region = BackdropRegion(left = 10, top = 20, right = 50, bottom = 60)
        assertEquals(40, region.width)
        assertEquals(40, region.height)

        val level1 = region.atLevel(1, 100, 100)
        assertEquals(5, level1.left)
        assertEquals(10, level1.top)
        assertEquals(25, level1.right)
        assertEquals(30, level1.bottom)

        val around = BackdropRegion.around(
            x = 20f,
            y = 20f,
            w = 40f,
            h = 40f,
            extent = 5,
            width = 100,
            height = 100,
        )
        assertEquals(15, around.left)
        assertEquals(15, around.top)
        assertEquals(65, around.right)
        assertEquals(65, around.bottom)
    }

    @Test
    fun backdropBlurEffectBuildsExactPassChain() {
        val blur = BlurChain(iterations = 3, offset = 1.0f, outputLevel = 1)
        val region = BackdropRegion(0, 0, 128, 128)
        val targetContext = ImagePassTargetContext(
            width = 128,
            height = 128,
            clipSpace = ClipSpace.Vulkan,
            snapshotSupported = true,
            effectQuality = EffectQuality.High,
        )

        val chain = BackdropBlurEffect.buildBlurChain(blur, region, targetContext)
        assertEquals(3, chain.transientTargets.size)
        // 3 downsamples + (3 - 1) = 2 upsamples = 5 steps total
        assertEquals(5, chain.steps.size)
        assertEquals(ImagePassSource.DestinationSnapshot, chain.steps[0].inputs[0].source)
    }

    @Test
    fun cpuReferenceTwinBlursBackdropCorrectly() {
        val width = 32
        val height = 32
        val pixels = FloatArray(width * height * 4) { 0f }
        // Draw a single bright red square in the center
        for (y in 12..19) {
            for (x in 12..19) {
                val idx = (y * width + x) * 4
                pixels[idx] = 1f
                pixels[idx + 1] = 0f
                pixels[idx + 2] = 0f
                pixels[idx + 3] = 1f
            }
        }

        val blur = BlurChain(iterations = 2, offset = 1f, outputLevel = 1)
        val region = BackdropRegion(0, 0, width, height)
        val blurred = BackdropBlurReference.blur(pixels, width, height, region, blur)

        val out = FloatArray(4)
        // Center pixel should still have red, but spread
        blurred.sample(15, 15, out)
        assertTrue(out[0] > 0.1f, "Center pixel should have positive red, got ${out[0]}")
        // Pixel just outside original square should now have received blurred red
        blurred.sample(10, 10, out)
        assertTrue(out[0] > 0.01f, "Spread pixel should have non-zero red, got ${out[0]}")
    }
}
