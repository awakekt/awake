/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertTrue

/** Pixel-level invariants for the default weighted font, independent of any browser screenshot. */
class FontRasterFidelityTest {

    private data class Ink(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val edgePixels: Int,
    ) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    private fun ink(size: Float, weight: FontWeight, text: String = "iliaeco"): Ink {
        val width = 240
        val height = 80
        val frame = composeFrame(width, height) {
            Text(text, style = TextStyle(size = size.sp, weight = weight))
        }
        val pixels = frame.primitives.rasterize(
            width,
            height,
            background = Color.Transparent,
            font = UiFonts.default(),
        )
        val covered = buildList {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val alpha = pixels[(y * width + x) * 4 + 3].toUByte().toInt()
                    if (alpha > 8) add(x to alpha to y)
                }
            }
        }
        require(covered.isNotEmpty()) { "font raster produced no ink for $text/$size/$weight" }
        return Ink(
            left = covered.minOf { it.first.first },
            top = covered.minOf { it.second },
            right = covered.maxOf { it.first.first },
            bottom = covered.maxOf { it.second },
            edgePixels = covered.count { it.first.second in 9..246 },
        )
    }

    @Test
    fun rasterizedInkGrowsAcrossUiTextSizes() {
        val heights = listOf(12f, 14f, 16f, 18f).map { ink(it, FontWeight.Normal).height }
        heights.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(larger > smaller, "raster ink stopped growing across sizes: $heights")
        }
    }

    @Test
    fun weightedFacesKeepFlatCapitalBaselinesAligned() {
        val bottoms = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)
            .map { ink(16f, it, text = "H").bottom }
        assertTrue(
            bottoms.max() - bottoms.min() <= 1,
            "weighted faces moved the flat-cap baseline: $bottoms",
        )
    }

    @Test
    fun distanceFieldRasterHasAntialiasedEdgeCoverage() {
        val edgePixels = ink(14f, FontWeight.Normal).edgePixels
        assertTrue(edgePixels > 0, "font raster emitted only binary or empty glyph edges")
    }
}
