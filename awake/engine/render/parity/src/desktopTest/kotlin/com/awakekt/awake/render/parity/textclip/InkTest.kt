/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity.textclip

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The lost-ink count behind the PR evidence's text-clipping sheets. Negative control: a count that
 * compares a render with itself reports 0 and fails every test below that expects a number.
 */
class InkTest {
    private fun image(w: Int, h: Int, ink: Map<Pair<Int, Int>, Int> = emptyMap()): BufferedImage {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val shade = 255 - (ink[x to y] ?: 0)
                image.setRGB(x, y, (0xFF shl 24) or (shade shl 16) or (shade shl 8) or shade)
            }
        }
        return image
    }

    @Test
    fun aFrameThatLosesNothingCountsZero() {
        val reference = image(4, 4, mapOf((1 to 1) to 200, (2 to 2) to 90))
        assertEquals(0, Ink.lost(reference, reference))
        assertEquals(0, Ink.extra(reference, reference))
    }

    @Test
    fun inkTheRenderLacksIsCountedOncePerPixel() {
        val reference = image(4, 4, mapOf((1 to 1) to 200, (2 to 1) to 90, (3 to 3) to 255))
        val render = image(4, 4, mapOf((2 to 1) to 90))
        assertEquals(2, Ink.lost(reference, render))
        assertEquals(0, Ink.extra(reference, render))
    }

    @Test
    fun aDifferenceBelowTheThresholdIsRoundingNotLostInk() {
        val reference = image(2, 2, mapOf((0 to 0) to Ink.THRESHOLD, (1 to 1) to Ink.THRESHOLD - 1))
        assertEquals(1, Ink.lost(reference, image(2, 2)))
    }

    @Test
    fun inkTheRenderAddsIsNotLost() {
        val render = image(3, 3, mapOf((1 to 1) to 128))
        assertEquals(0, Ink.lost(image(3, 3), render))
        assertEquals(1, Ink.extra(image(3, 3), render))
    }

    @Test
    fun inkOnAQuadsOuterRingIsCountedAndInkInsideIsNot() {
        val quad = Rect(2f, 2f, 6f, 6f)
        val inside = image(10, 10, mapOf((4 to 4) to 255))
        assertEquals(0, Ink.onQuadEdge(inside, listOf(quad)))
        val onEdge = image(10, 10, mapOf((4 to 4) to 255, (2 to 5) to 40, (7 to 7) to 255))
        assertEquals(2, Ink.onQuadEdge(onEdge, listOf(quad)))
    }
}
