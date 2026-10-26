/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Subpixel positioning of exact-size glyphs (#1005): the variants a pen's fraction picks from, the stem that
 * stays on the pixel grid in every one, and the gaps between digits that follow layout rather than the
 * rounding of each stem.
 */
class PixelGlyphSubpixelTest {
    private val font = RobotoOutlines.regular

    /** Every default character, so the glyphs are the ones a run draws and the page the one it uploads. */
    private val glyphs = OutlinePixelGlyphs(font, UiFonts.DEFAULT_CHARS.map { it.code }.toIntArray(), '?'.code, 0.93f)

    /** A glyph with ink has [OutlinePixelGlyphs.SUBPIXEL_STEPS] variants; one without has a single glyph for every step. */
    @Test
    fun everyGlyphWithInkHasVariantsAndASpaceHasOne() {
        val steps = OutlinePixelGlyphs.SUBPIXEL_STEPS
        for (char in "aHl0e") {
            assertEquals(steps, assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, 12)).subpixelSteps, "'$char'")
        }
        val space = assertNotNull(glyphs.glyph(' '.code, FontWeight.Normal, 12))
        assertEquals(1, space.subpixelSteps)
        for (step in 0 until steps) assertSame(space, glyphs.glyph(' '.code, FontWeight.Normal, 12, step))
        // A step past the last, or before the first, is the nearest one rather than an error.
        assertSame(glyphs.glyph('a'.code, FontWeight.Normal, 12, steps - 1), glyphs.glyph('a'.code, FontWeight.Normal, 12, steps + 5))
        assertSame(glyphs.glyph('a'.code, FontWeight.Normal, 12), glyphs.glyph('a'.code, FontWeight.Normal, 12, -1))
    }

    /** All of a size's variants live on the one page, so a run is drawn from one atlas whichever step each glyph takes. */
    @Test
    fun aBlocksVariantsShareOnePage() {
        val first = assertNotNull(glyphs.glyph('a'.code, FontWeight.Normal, 12))
        for (char in "aHel0") {
            for (step in 0 until first.subpixelSteps) assertSame(first.atlas, glyphs.glyph(char.code, FontWeight.Normal, 12, step)?.atlas)
        }
    }

    /**
     * A round glyph, with no stem to hold, moves with the pen: each step's bitmap sits a quarter of a pixel
     * right of the one before, measured by where its coverage is centred, to within the 1/255 a byte of
     * coverage resolves and the bitmap's own whole-pixel edge.
     */
    @Test
    fun stepsOfARoundGlyphMoveItByAFractionOfAPixel() {
        for (size in listOf(11, 12, 14)) {
            val steps = assertNotNull(glyphs.glyph('o'.code, FontWeight.Normal, size)).subpixelSteps
            val centres = (0 until steps).map { step -> assertNotNull(glyphs.glyph('o'.code, FontWeight.Normal, size, step)).let { it.left + inkCentre(it) } }
            for (step in 1 until steps) {
                assertEquals(step.toDouble() / steps, centres[step] - centres[0], 0.02, "'o' at $size px, step $step: centres $centres")
            }
        }
    }

    /**
     * Whichever variant the pen's fraction picks, a one-stem glyph's stem is one solid column with at most one
     * anti-aliased column beside it, and an `H`'s first stem is the solid first column of its bitmap, for the
     * default family and Medium loaded from a file.
     */
    @Test
    fun aStemIsOnTheGridInEveryVariant() = runTest {
        val loaded = UiFonts.load(readResourceBytes("Roboto-Medium.ttf"), "Roboto-Medium.ttf")
        for ((name, source) in listOf("default" to UiFonts.default().pixelGlyphs, "Roboto-Medium.ttf" to loaded.pixelGlyphs, "outline" to glyphs)) {
            val pixelGlyphs = assertNotNull(source, name)
            for (size in listOf(11, 12, 14)) {
                for (char in "lIH") {
                    val steps = assertNotNull(pixelGlyphs.glyph(char.code, FontWeight.Normal, size)).subpixelSteps
                    for (step in 0 until steps) {
                        val glyph = assertNotNull(pixelGlyphs.glyph(char.code, FontWeight.Normal, size, step))
                        val columns = columnPeaks(glyph)
                        val what = "$name '$char' at $size px, step $step: columns $columns"
                        if (char == 'H') {
                            assertTrue(columns.first() >= SOLID, what)
                        } else {
                            assertEquals(1, columns.count { it >= SOLID }, what)
                            assertTrue(columns.count { it > FAINT } <= 2, what)
                        }
                    }
                }
            }
        }
    }

    /**
     * The gaps between "0123456789" follow layout. Each digit is drawn where a renderer puts it, at the nearest
     * of its pen's subpixel steps; its ink's centre of coverage is compared with the centre of the same
     * digit rasterized exactly at its pen. A gap is the difference between neighbours' centres, and its
     * error the difference from the exact gap. Rounding each stem to a whole pixel put that error at
     * 0.51, 0.41, 0.41 and 0.44 pixels rms at 11, 12, 13 and 14 px, up to 0.82 at worst (measured before #1005,
     * with each digit's pen rounded at its stem, and a zero's curved side counted as a stem); the variants
     * bring it to 0.13, 0.05, 0.20 and 0.23 rms and 0.44 at worst, the rest being the stems of '1' and '4',
     * which stay on the grid. Drawn at the whole pixel nearest the pen, the digits miss by 0.42, 0.38, 0.51
     * and 0.30 rms and up to 0.96, more than the bounds below, so the check can fail.
     */
    @Test
    fun theGapsBetweenDigitsFollowLayout() {
        for (size in 11..14) {
            val variants = gapErrors("0123456789", size, subpixel = true)
            val whole = gapErrors("0123456789", size, subpixel = false)
            assertTrue(variants.rms() <= DIGIT_RMS_BOUND, "$size px: ${variants.rms()} rms gap error, $variants")
            assertTrue(variants.max() <= DIGIT_MAX_BOUND, "$size px: ${variants.max()} worst gap error, $variants")
            assertTrue(whole.rms() > variants.rms(), "$size px: whole pixels ${whole.rms()} rms is not worse than variants ${variants.rms()}")
        }
        val rounded = (11..14).flatMap { gapErrors("0123456789", it, subpixel = false) }
        assertTrue(rounded.rms() > DIGIT_RMS_BOUND && rounded.max() > DIGIT_MAX_BOUND, "whole-pixel digits: ${rounded.rms()} rms, ${rounded.max()} worst")
    }

    /**
     * "HP 87/100" at 15 px, a canvas at scale 1.25 with 12 sp text: Chromium leaves 3 clear pixels after the
     * '1' and 1 between the two zeros, the zeros' ink spanning 7 and 8 columns. Awake left 4 and 0 (both zeros
     * 8 columns, 56-63 and 64-71), the gap after the '1' too wide and the zeros touching, since a zero's
     * curved side counted as a stem and put each zero's left edge on a pixel (before #1005). A glyph is
     * only pinned when its stem is at least [MIN_STEM_SHARE] of its height, which a round glyph's straight
     * run never is.
     */
    @Test
    fun theHealthReadoutKeepsChromiumsGaps() {
        val gaps = inkGaps("HP 87/100", 15)
        assertEquals(listOf(null, 2, null, 1, 1, 1, 3, 1), gaps, "gaps in $gaps")
        val spans = inkSpans("HP 87/100", 15)
        assertEquals(listOf(7, 8), spans.takeLast(2).map { it.last - it.first + 1 }, "the zeros' ink spans")
    }

    /** The clear columns before each drawn glyph of [text] at [size] px, null at the first of a word, at coverage of 64/255 and up. */
    private fun inkGaps(text: String, size: Int): List<Int?> {
        val spans = inkSpans(text, size)
        var spanIndex = 0
        var previousEnd: Int? = null
        return text.map { char ->
            if (char == ' ') {
                previousEnd = null
                null
            } else {
                val span = spans[spanIndex++]
                (previousEnd?.let { span.first - it - 1 }).also { previousEnd = span.last }
            }
        }.filterIndexed { index, _ -> text[index] != ' ' }
    }

    /** The columns, from the pen at 0, that each drawn glyph of [text] at [size] px has ink of coverage 64/255 or more in. */
    private fun inkSpans(text: String, size: Int): List<IntRange> {
        val scale = size.toDouble() / font.unitsPerEm
        val steps = OutlinePixelGlyphs.SUBPIXEL_STEPS
        var pen = 0.0
        val spans = ArrayList<IntRange>()
        for (char in text) {
            if (char != ' ') {
                val step = floor(pen.toFloat() * steps + 0.5f).toInt()
                val whole = floor(step.toDouble() / steps).toInt()
                val glyph = assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size, step - whole * steps))
                val atlas = glyph.atlas
                val x0 = (glyph.u0 * atlas.atlasWidth + 0.5f).toInt()
                val y0 = (glyph.v0 * atlas.atlasHeight + 0.5f).toInt()
                val inked = (0 until glyph.width).filter { column ->
                    (0 until glyph.height).any { row -> (atlas.atlasPixelsRgba[((y0 + row) * atlas.atlasWidth + x0 + column) * 4 + 3].toInt() and 0xFF) >= 64 }
                }
                spans += (whole + glyph.left + inked.first())..(whole + glyph.left + inked.last())
            }
            pen += font.advance(font.glyphFor(char.code)) * scale
        }
        return spans
    }

    /** The error in each gap of [text] at [size] px, in pixels, drawn at subpixel steps or at the nearest whole pixel. */
    private fun gapErrors(text: String, size: Int, subpixel: Boolean): List<Double> {
        val scale = size.toDouble() / font.unitsPerEm
        val rasterizer = CoverageRasterizer()
        val lines = LineBuffer()
        val steps = OutlinePixelGlyphs.SUBPIXEL_STEPS
        val errors = ArrayList<Double>()
        var pen = 0.0
        var previous: Pair<Double, Double>? = null
        for (char in text) {
            val glyph = font.glyphFor(char.code)
            val drawn = if (subpixel) {
                val step = floor(pen * steps + 0.5)
                val whole = floor(step / steps)
                whole + 0.0 to assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size, (step - whole * steps).toInt()))
            } else {
                floor(pen + 0.5) to assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size))
            }
            val actual = drawn.first + drawn.second.left + inkCentre(drawn.second)
            val exact = rasterizeGlyph(font.outline(glyph), scale, VerticalHinting.IDENTITY, rasterizer, lines, alignStem = false, subpixel = pen - floor(pen))
            val ideal = floor(pen) + exact.left + inkCentre(exact)
            previous?.let { (actualBefore, idealBefore) -> errors += (actual - actualBefore) - (ideal - idealBefore) }
            previous = actual to ideal
            pen += font.advance(glyph) * scale
        }
        return errors
    }

    private fun List<Double>.rms(): Double = sqrt(sumOf { it * it } / size)

    private fun List<Double>.max(): Double = maxOf { abs(it) }

    /** The x, in pixels from the bitmap's left edge, that [glyph]'s coverage is centred on. */
    private fun inkCentre(glyph: PixelGlyph): Double {
        val atlas = glyph.atlas
        val x0 = (glyph.u0 * atlas.atlasWidth + 0.5f).toInt()
        val y0 = (glyph.v0 * atlas.atlasHeight + 0.5f).toInt()
        return centre(glyph.width, glyph.height) { column, row ->
            atlas.atlasPixelsRgba[((y0 + row) * atlas.atlasWidth + x0 + column) * 4 + 3].toInt() and 0xFF
        }
    }

    private fun inkCentre(bitmap: GlyphBitmap): Double =
        centre(bitmap.width, bitmap.height) { column, row -> bitmap.mask[row * bitmap.width + column].toInt() and 0xFF }

    private fun centre(width: Int, height: Int, alpha: (Int, Int) -> Int): Double {
        var moment = 0.0
        var total = 0.0
        for (row in 0 until height) {
            for (column in 0 until width) {
                moment += (column + 0.5) * alpha(column, row)
                total += alpha(column, row)
            }
        }
        return moment / total
    }

    /** Each column's strongest coverage, across the glyph's middle rows (clear of serifs and terminals). */
    private fun columnPeaks(glyph: PixelGlyph): List<Int> {
        val atlas = glyph.atlas
        val x0 = (glyph.u0 * atlas.atlasWidth + 0.5f).toInt()
        val y0 = (glyph.v0 * atlas.atlasHeight + 0.5f).toInt()
        val rows = (glyph.height / 4) until (glyph.height * 3 / 4).coerceAtLeast(glyph.height / 4 + 1)
        return (0 until glyph.width).map { column ->
            rows.maxOf { row -> atlas.atlasPixelsRgba[((y0 + row) * atlas.atlasWidth + x0 + column) * 4 + 3].toInt() and 0xFF }
        }
    }

    private companion object {
        const val SOLID = 250
        const val FAINT = 12

        /** Rms and worst gap error, in pixels, "0123456789" must stay under at 11 to 14 px. */
        const val DIGIT_RMS_BOUND = 0.26
        const val DIGIT_MAX_BOUND = 0.5
    }
}
