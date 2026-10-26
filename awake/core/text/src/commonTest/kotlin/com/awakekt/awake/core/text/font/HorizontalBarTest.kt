/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Horizontal bars on whole pixel rows (#1005): found from the outline, and snapped, so the crossbar of an
 * `e` and the bars of `H`, `A`, `F`, `E` and `B` are one solid row, not two grey ones.
 */
class HorizontalBarTest {
    private val font = RobotoOutlines.regular
    private val unitsPerEm = font.unitsPerEm.toDouble()

    private fun barsOf(char: Char): List<Pair<Int, Int>> =
        horizontalBars(font.outline(font.glyphFor(char.code)), unitsPerEm).map { it.bottom.toInt() to it.top.toInt() }

    /** Each glyph's bars, as Roboto Regular draws them: the flat edges of its crossbars, arms and feet. */
    @Test
    fun theBarsAreFoundFromTheOutline() {
        assertEquals(listOf(489 to 640), barsOf('e'))
        assertEquals(listOf(673 to 830), barsOf('H'))
        assertEquals(listOf(381 to 539), barsOf('A'))
        assertEquals(listOf(643 to 801, 1298 to 1456), barsOf('F'))
        assertEquals(listOf(0 to 157, 673 to 830, 1298 to 1456), barsOf('E'))
        assertEquals(listOf(0 to 157, 681 to 836, 1298 to 1456), barsOf('B'))
        assertEquals(listOf(940 to 1082), barsOf('t'))
        // A round glyph and a lone stem have no flat bar: a stem's foot is not one.
        for (char in "ol1I|") assertEquals(emptyList(), barsOf(char), "'$char'")
    }

    /** The heights are the same whatever the outline's winding: a CFF font winds its contours the other way. */
    @Test
    fun aReversedOutlineHasTheSameBars() {
        val outline = font.outline(font.glyphFor('H'.code))
        val reversed = outline.reversed().map { segment ->
            val p = segment.points
            OutlineSegment(doubleArrayOf(p[6], p[7], p[4], p[5], p[2], p[3], p[0], p[1]), segment.isLine)
        }
        val bars = horizontalBars(reversed, unitsPerEm)
        assertEquals(listOf(673 to 830), bars.map { it.bottom.toInt() to it.top.toInt() })
    }

    /**
     * At 12 px an `e`'s crossbar and an `H`'s bar are one row of solid pixels with nothing but counter above
     * and below it, where unsnapped each is two grey rows ([withoutTheSnapABarIsTwoGreyRows]).
     */
    @Test
    fun aCrossbarIsOneSolidRowAtTwelvePixels() {
        val glyphs = UiFonts.default().pixelGlyphs!!
        for (char in "eH") {
            val glyph = assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, 12))
            val bar = barRow(glyph)
            val solid = (0 until glyph.width).filter { alpha(glyph, it, bar) >= SOLID_ROW }
            assertTrue(solid.size >= 3, "'$char': row $bar holds only ${solid.size} solid pixels")
            // The middle of the bar, clear of where the bowl or the stems join it.
            val middle = solid.subList(solid.size / 2 - 1, solid.size / 2 + 1)
            for (column in middle) {
                assertTrue(alpha(glyph, column, bar - 1) <= COUNTER, "'$char': ${alpha(glyph, column, bar - 1)} above the bar at column $column")
                assertTrue(alpha(glyph, column, bar + 1) <= COUNTER, "'$char': ${alpha(glyph, column, bar + 1)} below the bar at column $column")
            }
        }
    }

    /**
     * The same at 11 to 14 px, and every bar of `A`, `F`, `E`, `B`, `P` and `R`, whose bars are at other
     * heights, is a row of three solid pixels or more.
     */
    @Test
    fun everyBarIsASolidRowAtEverySize() {
        val glyphs = UiFonts.default().pixelGlyphs!!
        for (size in listOf(11, 12, 13, 14)) {
            for (char in "AFEBPR") {
                val glyph = assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size))
                val rows = (0 until glyph.height).filter { row -> (0 until glyph.width).count { alpha(glyph, it, row) >= SOLID_ROW } >= 3 }
                assertTrue(rows.size >= barsOf(char).size, "'$char' at $size px: solid rows $rows for ${barsOf(char).size} bars")
            }
            for (char in "eH") {
                val glyph = assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size))
                val bar = barRow(glyph)
                val solid = (0 until glyph.width).filter { alpha(glyph, it, bar) >= SOLID_ROW }
                assertTrue(solid.size >= 3, "'$char' at $size px: row $bar holds only ${solid.size} solid pixels")
                for (column in solid.subList(solid.size / 2 - 1, solid.size / 2 + 1)) {
                    assertTrue(alpha(glyph, column, bar - 1) <= COUNTER && alpha(glyph, column, bar + 1) <= COUNTER, "'$char' at $size px: column $column is not clear around the bar")
                }
            }
        }
    }

    /** The baseline, x-height and cap-height snaps hold with the bars snapped: a flat top and a flat foot stay solid. */
    @Test
    fun snappingBarsKeepsTheHeightSnaps() {
        val glyphs = UiFonts.default().pixelGlyphs!!
        for (size in listOf(11, 12, 14)) {
            for (char in "EFTZz") {
                val glyph = assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size))
                assertTrue((0 until glyph.width).maxOf { alpha(glyph, it, 0) } >= SOLID_ROW, "'$char' at $size px: its top row is not solid")
            }
            for (char in "EZz") {
                val glyph = assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size))
                assertEquals(0, glyph.top + glyph.height, "'$char' at $size px ends ${glyph.top + glyph.height} px from the baseline")
                assertTrue((0 until glyph.width).maxOf { alpha(glyph, it, glyph.height - 1) } >= SOLID_ROW, "'$char' at $size px: its foot is not solid")
            }
        }
    }

    /**
     * Negative control: with the bars left where the height snaps put them, the same two glyphs at 12 px have
     * no row of three solid pixels. Each one's best row has three pixels at least as strong as [E_UNSNAPPED_LEVEL]
     * for the `e` and [H_UNSNAPPED_LEVEL] for the `H`, out of 255, which is the grey of a bar split over two rows.
     */
    @Test
    fun withoutTheSnapABarIsTwoGreyRows() {
        val scale = 12.0 / font.unitsPerEm
        val unsnapped = VerticalHinting.measure(font).at(scale)
        for ((char, level) in listOf('e' to E_UNSNAPPED_LEVEL, 'H' to H_UNSNAPPED_LEVEL)) {
            val bitmap = rasterizeGlyph(font.outline(font.glyphFor(char.code)), scale, unsnapped, CoverageRasterizer(), LineBuffer())
            assertEquals(level, threeStrongest(bitmap), "'$char' unsnapped")
            assertTrue(level < SOLID_ROW)
        }
        val snapped = UiFonts.default().pixelGlyphs!!
        for (char in "eH") {
            val glyph = assertNotNull(snapped.glyph(char.code, FontWeight.Normal, 12))
            assertTrue((1 until glyph.height - 1).maxOf { row -> (0 until glyph.width).map { alpha(glyph, it, row) }.sortedDescending()[2] } >= SOLID_ROW)
        }
    }

    /** The coverage three pixels of one of [bitmap]'s rows, not its first or last, all reach: its best row's third strongest pixel. */
    private fun threeStrongest(bitmap: GlyphBitmap): Int = (1 until bitmap.height - 1).maxOf { row ->
        (0 until bitmap.width).map { bitmap.mask[row * bitmap.width + it].toInt() and 0xFF }.sortedDescending()[2]
    }

    /** The map moves a bar's edges onto whole rows and leaves every pin where it was. */
    @Test
    fun theMapPutsBarEdgesOnWholeRowsAndKeepsThePins() {
        val scale = 12.0 / font.unitsPerEm
        val base = VerticalHinting.measure(font).at(scale)
        val outline = font.outline(font.glyphFor('H'.code))
        val map = base.snappingBars(horizontalBars(outline, unitsPerEm), scale)
        val xTop = checkNotNull(outlineBounds(font.outline(font.glyphFor('x'.code))))[3]
        val capTop = checkNotNull(outlineBounds(outline))[3]
        assertEquals(base.pins[1], map(xTop * scale), 1e-9, "x-height")
        assertEquals(base.pins[2], map(capTop * scale), 1e-9, "cap height")
        assertEquals(0.0, map(0.0), 1e-9, "baseline")
        val bottom = map(673 * scale)
        val top = map(830 * scale)
        assertEquals(bottom, kotlin.math.floor(bottom + 0.5), 1e-9)
        assertEquals(1.0, top - bottom, 1e-9)
        assertEquals(-1.0, map(-1.0), 1e-9, "below the baseline")
    }

    /** The row of [glyph] that holds its bar: the one with the most solid pixels, not counting its first and last rows. */
    private fun barRow(glyph: PixelGlyph): Int =
        (1 until glyph.height - 1).maxBy { row -> (0 until glyph.width).count { alpha(glyph, it, row) >= SOLID_ROW } }

    private fun alpha(glyph: PixelGlyph, column: Int, row: Int): Int {
        val atlas = glyph.atlas
        val x = (glyph.u0 * atlas.atlasWidth + 0.5f).toInt() + column
        val y = (glyph.v0 * atlas.atlasHeight + 0.5f).toInt() + row
        return atlas.atlasPixelsRgba[(y * atlas.atlasWidth + x) * 4 + 3].toInt() and 0xFF
    }

    private companion object {
        /** A row resolves to 1/16 of a pixel down the glyph, so a full one can read 15/16. */
        const val SOLID_ROW = 238

        /** Coverage a counter, with no ink in it, stays under. */
        const val COUNTER = 40
        const val BAR_HALF = 100
        const val E_UNSNAPPED_LEVEL = 197
        const val H_UNSNAPPED_LEVEL = 172
    }
}
