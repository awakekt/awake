/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A font [UiFonts.load] bakes draws each glyph's distance-field fringe out to nothing inside its own
 * sample rect: the rect reaches past the ink at least as far as the field does, so a quad's edge
 * pixels read zero coverage rather than a hard cut through the fringe (#1053).
 *
 * Coverage is the shader's: `screenPxRange * (field - 0.5) + 0.5`, with `screenPxRange` half the
 * field's range in screen pixels and never below 1, at each size the distance field is drawn at.
 */
class DistanceFieldCellEdgeTest {
    @Test
    fun aLoadedFontsFringeFadesOutInsideEachGlyphsRect() = runTest {
        val font = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf", UiFonts.DEFAULT_CHARS + "ÅÉÑÇÿ") as PackedUiFont
        val chars = UiFonts.DEFAULT_CHARS + "ÅÉÑÇÿ"
        val failures = listOf(12, 16, 18, 25, 32, 48, 96).mapNotNull { size ->
            val worst = chars.maxOf { edgeCoverage(font, it, size) }
            val char = chars.first { edgeCoverage(font, it, size) == worst }
            if (worst > 0f) "${size}px: coverage ${(worst * 1000).roundToInt() / 1000f} at the edge of '$char'" else null
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * The highest coverage along the border of [char]'s sample rect drawn [size] pixels to the em, where a
     * quad's edge samples: halfway between its outermost texel and the one past it. 0 for no ink.
     */
    private fun edgeCoverage(font: PackedUiFont, char: Char, size: Int): Float {
        val glyph = font.glyphFor(char, FontWeight.Normal)?.takeIf { it.widthEm > 0f && font.hasGlyph(char) } ?: return 0f
        val x0 = (glyph.u0 * font.atlasWidth).roundToInt()
        val x1 = (glyph.u1 * font.atlasWidth).roundToInt() - 1
        val y0 = (glyph.v0 * font.atlasHeight).roundToInt()
        val y1 = (glyph.v1 * font.atlasHeight).roundToInt() - 1
        val texelsPerEm = (x1 - x0 + 1) / glyph.widthEm
        val screenPxRange = max(0.5f * font.distanceFieldRangePx * size / texelsPerEm, 1f)
        fun coverage(field: Float): Float = (screenPxRange * (field - 0.5f) + 0.5f).coerceIn(0f, 1f)
        fun border(x: Int, y: Int, outX: Int, outY: Int): Float = coverage((field(font, x, y) + field(font, outX, outY)) / 2f)
        var edge = 0f
        for (x in x0..x1) edge = max(edge, max(border(x, y0, x, y0 - 1), border(x, y1, x, y1 + 1)))
        for (y in y0..y1) edge = max(edge, max(border(x0, y, x0 - 1, y), border(x1, y, x1 + 1, y)))
        return edge
    }

    /** The field at one atlas texel, clamped to the atlas: the median of its three channels, as the shader reads it. */
    private fun field(font: PackedUiFont, atX: Int, atY: Int): Float {
        val x = atX.coerceIn(0, font.atlasWidth - 1)
        val y = atY.coerceIn(0, font.atlasHeight - 1)
        val i = (y * font.atlasWidth + x) * 4
        val r = (font.atlasPixelsRgba[i].toInt() and 0xFF) / 255f
        val g = (font.atlasPixelsRgba[i + 1].toInt() and 0xFF) / 255f
        val b = (font.atlasPixelsRgba[i + 2].toInt() and 0xFF) / 255f
        return max(min(r, g), min(max(r, g), b))
    }
}
