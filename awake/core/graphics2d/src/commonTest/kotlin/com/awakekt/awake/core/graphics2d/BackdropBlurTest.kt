/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackdropBlurTest {

    /**
     * The radius is a standard deviation, as CSS `blur()`: a line blurred through the CPU twin of
     * the GPU passes spreads by the requested radius, within 10%, and by what [DualKawaseBlur.sigma]
     * predicts, within 1%. Measured: 1.0, 1.5, 2.0, 3.21 (the gap between one and two halvings),
     * 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0, 48.0 and 64.0 px.
     *
     * Negative control: with the bilinear reads left out of [DualKawaseBlur.variance] every blur
     * came out wider than asked: radius 1 spread by 1.10 and radius 3 by 3.56, 19% over, failing
     * both checks.
     */
    @Test
    fun anImpulseSpreadsByTheRequestedRadius() {
        for (radius in listOf(1f, 1.5f, 2f, 3f, 4f, 6f, 8f, 12f, 16f, 24f, 32f, 48f, 64f)) {
            val blur = DualKawaseBlur.forRadius(radius, BackdropBlurQuality.High)
            val measured = measuredSigma(blur)
            assertTrue(abs(measured - radius) / radius < 0.1f, "radius $radius ran $blur and spread by $measured px")
            assertEquals(blur.sigma, measured, radius * 0.01f, "the model predicts what the passes do at radius $radius")
        }
    }

    @Test
    fun aWiderRadiusHalvesMoreOften() {
        val small = DualKawaseBlur.forRadius(3f, BackdropBlurQuality.High)
        val large = DualKawaseBlur.forRadius(40f, BackdropBlurQuality.High)
        assertTrue(large.iterations > small.iterations, "$small vs $large")
        assertTrue(large.extent > small.extent)
    }

    @Test
    fun aLowerQualityCapsTheRadiusAndSamplesACoarserLevel() {
        val high = DualKawaseBlur.forRadius(200f, BackdropBlurQuality.High)
        val low = DualKawaseBlur.forRadius(200f, BackdropBlurQuality.Low)
        assertEquals(BackdropBlurQuality.High.maxIterations, high.iterations)
        assertEquals(BackdropBlurQuality.Low.maxIterations, low.iterations)
        assertEquals(2, low.outputLevel)
        assertEquals(DualKawaseBlur.MAX_OFFSET, low.offset)
        assertTrue(low.sigma < high.sigma)
        assertTrue(low.passCount < high.passCount)
    }

    @Test
    fun offRunsNoBlur() {
        assertFailsWith<IllegalArgumentException> { DualKawaseBlur.forRadius(8f, BackdropBlurQuality.Off) }
    }

    @Test
    fun theFallbackIsTheTintMadeMoreOpaque() {
        assertEquals(Color(1f, 1f, 1f, 0.6f), backdropFallbackColor(Color(1f, 1f, 1f, 0f)))
        assertEquals(1f, backdropFallbackColor(Color(0.2f, 0.3f, 0.4f, 1f)).a)
        assertEquals(0.8f, backdropFallbackColor(Color(0f, 0f, 0f, 0.5f)).a, 1e-6f)
    }

    @Test
    fun aRegionGrowsByTheExtentAndStaysInsideTheBackdrop() {
        val region = BackdropRegion.around(10.5f, 20f, 30f, 40f, extent = 16, width = 100, height = 50)
        assertEquals(BackdropRegion(0, 4, 57, 50), region)
        assertEquals(BackdropRegion(0, 1, 15, 13), region.atLevel(2, 100, 50))
    }

    @Test
    fun aRegionDoesNotLeakTheBackdropAroundIt() {
        // A bright frame around a dark region: the blur reads only the region, so nothing of the
        // frame reaches its centre even though the blur is wider than the region.
        val size = 64
        val pixels = FloatArray(size * size * 4) { 1f }
        val region = BackdropRegion(16, 16, 48, 48)
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                val index = (y * size + x) * 4
                pixels[index] = 0f
                pixels[index + 1] = 0f
                pixels[index + 2] = 0f
            }
        }
        val blurred = BackdropBlurReference.blur(pixels, size, size, region, DualKawaseBlur.forRadius(24f, BackdropBlurQuality.High))
        val out = FloatArray(4)
        blurred.sample(32, 32, out)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(1f, out[3], 1e-6f)
    }

    /**
     * The spread of a one-pixel vertical line, which is the horizontal profile of the 2D kernel,
     * summed over every position the line can take against the coarsest level. A blur halving `n`
     * times treats the `2^n` positions differently, and a picture holds all of them at once.
     */
    private fun measuredSigma(blur: DualKawaseBlur): Float {
        val period = 1 shl blur.iterations
        val margin = blur.extent + period
        val width = 2 * margin + period
        val height = 2 * period
        val profile = DoubleArray(2 * margin + 1)
        val out = FloatArray(4)
        for (phase in 0 until period) {
            val line = margin + phase
            val pixels = FloatArray(width * height * 4)
            for (y in 0 until height) pixels[(y * width + line) * 4] = 1f
            val blurred = BackdropBlurReference.blur(pixels, width, height, BackdropRegion(0, 0, width, height), blur)
            for (x in 0 until width) {
                val distance = x - line
                if (distance < -margin || distance > margin) continue
                blurred.sample(x, height / 2, out)
                profile[distance + margin] += out[0].toDouble()
            }
        }
        var weight = 0.0
        var second = 0.0
        for (index in profile.indices) {
            val distance = (index - margin).toDouble()
            weight += profile[index]
            second += profile[index] * distance * distance
        }
        return sqrt(second / weight).toFloat()
    }
}
