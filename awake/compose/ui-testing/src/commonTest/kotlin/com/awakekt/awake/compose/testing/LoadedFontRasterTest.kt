/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.ui.platform.LocalFont
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Text drawn with Roboto Regular loaded at run time lands where the built-in Roboto Regular puts it.
 *
 * Both draw through the same layout and the same distance-field sampling. The two controls are what
 * make the comparison worth reading: Roboto Bold, and the loaded font moved by two pixels, must both
 * fail it.
 */
class LoadedFontRasterTest {

    private class Ink(val alpha: IntArray, val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun ink(font: UiFont, shiftX: Float = 0f): Ink {
        val frame = composeFrame(WIDTH, HEIGHT) {
            CompositionLocalProvider(LocalFont, font) {
                Text(SAMPLE, style = TextStyle(size = 16.sp))
            }
        }
        val primitives = frame.primitives.map { if (it is UiDrawPrimitive.Glyph) it.copy(x = it.x + shiftX) else it }
        val pixels = primitives.rasterize(WIDTH, HEIGHT, background = Color.Transparent, font = font)
        val alpha = IntArray(WIDTH * HEIGHT) { pixels[it * 4 + 3].toInt() and 0xFF }
        val inked = alpha.indices.filter { alpha[it] > INK_ALPHA }
        require(inked.isNotEmpty()) { "no ink drawn" }
        return Ink(
            alpha = alpha,
            left = inked.minOf { it % WIDTH },
            top = inked.minOf { it / WIDTH },
            right = inked.maxOf { it % WIDTH },
            bottom = inked.maxOf { it / WIDTH },
        )
    }

    /** The ink box within a pixel on every side, and the summed per-pixel alpha difference under [MAX_ALPHA_DIFFERENCE] of the ink. */
    private fun sameInk(expected: Ink, actual: Ink): Pair<Boolean, String> {
        val edges = listOf(expected.left - actual.left, expected.top - actual.top, expected.right - actual.right, expected.bottom - actual.bottom)
        val difference = expected.alpha.indices.sumOf { abs(expected.alpha[it] - actual.alpha[it]) }.toDouble() / expected.alpha.sum()
        val same = edges.all { abs(it) <= 1 } && difference <= MAX_ALPHA_DIFFERENCE
        return same to "edges off by $edges, alpha differs by ${difference * 100}% of the ink"
    }

    @Test
    fun loadedRobotoDrawsWhereTheBuiltInRobotoDraws() = runTest {
        val loaded = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf")
        val baked = ink(UiFonts.trueSans())
        val (same, report) = sameInk(baked, ink(loaded))
        assertTrue(same, report)

        val (boldSame, boldReport) = sameInk(baked, ink(UiFonts.load(readResourceBytes("Roboto-Bold.ttf"), "Roboto-Bold.ttf")))
        assertTrue(!boldSame, "Roboto Bold passed for Regular: $boldReport")
        val (shiftedSame, shiftedReport) = sameInk(baked, ink(loaded, shiftX = 2f))
        assertTrue(!shiftedSame, "a two-pixel shift passed: $shiftedReport")
    }

    private companion object {
        const val WIDTH = 360
        const val HEIGHT = 40
        const val SAMPLE = "Sphinx of black quartz, judge my vow 0123?"
        const val INK_ALPHA = 8
        const val MAX_ALPHA_DIFFERENCE = 0.05
    }
}
