/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A pixel-aligned border on a thin, fully rounded bar is solid along its straight edges and leaves
 * the rows inside them untouched, as on any other rounded rectangle.
 */
class ThinPillBorderTest {
    @Test
    fun aThinPillsStraightEdgesAreOneSolidBandWithNothingInside() {
        val cases = (3..8).flatMap { h ->
            listOf(1, 2).filter { 2 * it < h }.flatMap { stroke ->
                listOf(93, 40, 17).flatMap { w -> listOf(h / 2f, h / 2f - 0.5f).map { radius -> Pill(w, h, radius, stroke) } }
            }
        }
        val failures = cases.mapNotNull { pill -> pill.interiorMismatch() }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    private data class Pill(val w: Int, val h: Int, val radius: Float, val stroke: Int) {
        /** The rows down the bar's middle column, as coverage, when they are not a solid band with nothing inside. */
        fun interiorMismatch(): String? {
            val pixels = listOf(UiDrawPrimitive.RoundedQuad(X.toFloat(), Y.toFloat(), w.toFloat(), h.toFloat(), Ink, radius, strokeWidth = stroke.toFloat()))
                .rasterize(WIDTH, HEIGHT, background = Paper)
            val column = X + w / 2
            val rows = (Y until Y + h).map { row -> 255 - (pixels[(row * WIDTH + column) * 4].toInt() and 0xFF) }
            val expected = (0 until h).map { if (it < stroke || it >= h - stroke) 255 else 0 }
            return if (rows == expected) null else "${w}x$h r=$radius stroke=$stroke: $rows"
        }
    }

    private companion object {
        const val X = 8
        const val Y = 8
        const val WIDTH = 120
        const val HEIGHT = 24
        val Ink = Color(0f, 0f, 0f, 1f)
        val Paper = Color(1f, 1f, 1f, 1f)
    }
}
