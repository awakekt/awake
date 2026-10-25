/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A text box with the font's own line height holds all of its ink, at every density: clipping the text to
 * its box removes nothing above or below it.
 *
 * The CSS line box rounds the font's ascent and descent to whole density-independent pixels, so at a
 * fractional density, or above the exact-size pixel-glyph limit, where the distance field's antialiased
 * edge reaches past the outline, it can end inside the descenders: Roboto's parenthesis lost its bottom row
 * at 9 sp on a 0.75 density canvas and at 24 sp at 125% and 150%.
 */
class TextLineBoxInkTest {
    private val black = Color(0f, 0f, 0f, 1f)
    private val white = Color(1f, 1f, 1f, 1f)

    @Test
    fun theReportedCasesKeepTheirBottomRow() {
        assertEquals(0, rowsLost("gjpqy", 9, 0.75f), "gjpqy at 9 sp, density 0.75")
        assertEquals(0, rowsLost("|([{}])", 9, 0.75f), "|([{}]) at 9 sp, density 0.75")
        assertEquals(0, rowsLost("|([{}])", 24, 1.25f), "|([{}]) at 24 sp, density 1.25")
        assertEquals(0, rowsLost("|([{}])", 24, 1.5f), "|([{}]) at 24 sp, density 1.5")
    }

    @Test
    fun noSizeOrDensityClipsTheDeepestInk() {
        val cases = listOf("|([{}])", "gjpqy").flatMap { text ->
            listOf(9, 11, 12, 14, 16, 18, 24, 28, 32, 48).flatMap { size ->
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { density -> Triple(text, size, density) }
            }
        }
        val clipped = cases.mapNotNull { (text, size, density) ->
            rowsLost(text, size, density).takeIf { it > 0 }?.let { "$text ${size}sp d$density: $it rows" }
        }
        assertEquals(emptyList(), clipped)
    }

    /** Rows above or below the text's own box that lose ink when the text is clipped to that box. */
    private fun rowsLost(text: String, size: Int, density: Float): Int {
        val style = TextStyle(color = black, size = size.sp, font = UiFonts.default())
        val width = (size * density * 8 + 100 * density).toInt()
        val height = (size * density * 2.5f + 100 * density).toInt()
        val free = render(density, width, height) { Box(Modifier.padding(40.dp)) { Text(text, style = style) } }
        val clippedFrame = ComposeHost(density = density).frame(FrameInput(width, height)) {
            Box(Modifier.padding(40.dp)) { Text(text, Modifier.clipToBounds(), style = style) }
        }.primitives
        val clipped = clippedFrame.rasterize(width, height, background = white, font = UiFonts.default())
        val box = clippedFrame.filterIsInstance<UiDrawPrimitive.ClipPush>().first().rect
        var rows = 0
        for (y in 0 until height) {
            if (y >= box.y && y < box.y + box.height) continue
            val lost = (0 until width).any { x -> ink(clipped, width, x, y) + INK_THRESHOLD <= ink(free, width, x, y) }
            if (lost) rows++
        }
        return rows
    }

    private fun render(density: Float, width: Int, height: Int, content: context(Composer) () -> Unit): ByteArray =
        ComposeHost(density = density).frame(FrameInput(width, height), content).primitives
            .rasterize(width, height, background = white, font = UiFonts.default())

    private fun ink(pixels: ByteArray, width: Int, x: Int, y: Int): Int = 255 - (pixels[(y * width + x) * 4].toInt() and 0xFF)

    private companion object {
        /** The faintest difference counted as lost ink, out of 255. */
        const val INK_THRESHOLD = 8
    }
}
