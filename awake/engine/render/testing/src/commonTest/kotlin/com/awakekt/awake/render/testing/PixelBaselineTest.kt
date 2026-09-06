/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.capture.RgbaSample
import com.awakekt.awake.render.testing.summarize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixelBaselineTest {

    @Test
    fun identicalBuffersMatch() {
        val pixels = byteArrayOf(10, 20, 30, -1, 40, 50, 60, -1)
        val result = comparePixels(pixels, pixels.copyOf())
        assertTrue(result.matches)
        assertEquals(0, result.diffPixelCount)
        assertEquals(0, result.maxChannelDiff)
    }

    @Test
    fun diffWithinToleranceStillMatches() {
        val actual = byteArrayOf(10, 20, 30, -1)
        val expected = byteArrayOf(11, 20, 31, -1)
        val result = comparePixels(actual, expected, toleranceParChannel = 2)
        assertTrue(result.matches, "a 1-value-per-channel diff must pass a tolerance of 2")
        assertEquals(0, result.diffPixelCount)
        assertEquals(1, result.maxChannelDiff)
    }

    @Test
    fun diffBeyondToleranceFails() {
        val actual = byteArrayOf(10, 20, 30, -1, 100, 100, 100, -1)
        val expected = byteArrayOf(10, 20, 30, -1, 0, 100, 100, -1)
        val result = comparePixels(actual, expected, toleranceParChannel = 2)
        assertFalse(result.matches)
        assertEquals(1, result.diffPixelCount, "only the second pixel should count as mismatched")
        assertEquals(100, result.maxChannelDiff)
    }

    @Test
    fun sizeMismatchThrows() {
        assertFailsWith<IllegalArgumentException> {
            comparePixels(byteArrayOf(1, 2, 3, 4), byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        }
    }

    @Test
    fun pixelProbeSummarySamplesExpectedAnchorPixels() {
        val pixels = byteArrayOf(
            1, 2, 3, 4,
            5, 6, 7, 8,
            9, 10, 11, 12,
            13, 14, 15, 16,
        )

        val summary = PixelMap(width = 2, height = 2, pixels = pixels).summarize()

        assertEquals(RgbaSample(1, 2, 3, 4), summary.topLeft)
        assertEquals(RgbaSample(5, 6, 7, 8), summary.topRight)
        assertEquals(RgbaSample(9, 10, 11, 12), summary.bottomLeft)
        assertEquals(RgbaSample(13, 14, 15, 16), summary.bottomRight)
        assertEquals(RgbaSample(13, 14, 15, 16), summary.center)
    }

    @Test
    fun compositeOverUsesOpaqueBackground() {
        val source = PixelMap(1, 1)
        source.set(0, 0, Color(1f, 0f, 0f, 0.5f))

        val result = source.compositeOver(Color(0f, 0f, 1f, 1f)).sample(0, 0)

        assertEquals(RgbaSample(127, 0, 128, 255), result)
    }
}
