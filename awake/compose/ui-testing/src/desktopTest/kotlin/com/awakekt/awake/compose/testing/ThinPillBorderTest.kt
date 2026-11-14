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
        val failures = mutableListOf<String>()
        for (h in 3..8) {
            for (stroke in listOf(1, 2)) {
                for (w in listOf(93, 40, 17)) {
                    for (radius in listOf(h / 2f, h / 2f - 0.5f)) {
                        if (2 * stroke >= h) continue
                        val x = 8
                        val y = 8
                        val pixels = listOf(UiDrawPrimitive.RoundedQuad(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), Ink, radius, strokeWidth = stroke.toFloat()))
                            .rasterize(WIDTH, HEIGHT, background = Paper)
                        val column = x + w / 2
                        val rows = (y until y + h).map { row -> 255 - (pixels[(row * WIDTH + column) * 4].toInt() and 0xFF) }
                        val expected = (0 until h).map { if (it < stroke || it >= h - stroke) 255 else 0 }
                        if (rows != expected) failures += "${w}x$h r=$radius stroke=$stroke: $rows"
                    }
                }
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 24
        val Ink = Color(0f, 0f, 0f, 1f)
        val Paper = Color(1f, 1f, 1f, 1f)
    }
}
