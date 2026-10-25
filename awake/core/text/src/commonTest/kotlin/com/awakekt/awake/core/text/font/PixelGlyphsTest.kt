/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Exact-size coverage glyphs: the rasterizer against a supersampled reference, the stems and heights the
 * light hinting puts on the pixel grid, the cache, and the fonts that offer them or opt out.
 */
class PixelGlyphsTest {
    private val sizes = listOf(11, 12, 14)

    /**
     * The scanline rasterizer's coverage against a 16 by 16 supersampled non-zero winding test of the
     * same outline, unhinted, including where Roboto's overlapping contours meet: within the reference's own
     * sampling error.
     */
    @Test
    fun coverageMatchesASupersampledReference() {
        val font = RobotoOutlines.regular
        val rasterizer = CoverageRasterizer()
        val lines = LineBuffer()
        for (size in sizes) {
            val scale = size.toDouble() / font.unitsPerEm
            val errors = "Hamburgefonstiv@&%0123".flatMap { char ->
                val outline = font.outline(font.glyphFor(char.code))
                val bitmap = rasterizeGlyph(outline, scale, VerticalHinting.IDENTITY, rasterizer, lines, alignStem = false)
                errorsAgainstReference(outline, scale, bitmap)
            }
            val pixels = errors.size
            val close = errors.count { it <= SAMPLING_TOLERANCE }
            val worst = errors.max()
            val share = close.toDouble() / pixels
            assertTrue(share >= 0.995, "$size px: only ${share * 100}% of $pixels pixels within $SAMPLING_TOLERANCE of the reference")
            assertTrue(worst <= WORST_TOLERANCE, "$size px: a pixel is off the reference by $worst")
        }
    }

    /**
     * Light hinting puts the stem of a one-stem glyph on the pixel grid: one solid column, and at most one
     * anti-aliased column beside it, for Roboto Regular (the default family) and Medium loaded from a file.
     */
    @Test
    fun aOnePixelStemIsOneSolidColumnOnTheGrid() = runTest {
        val loaded = UiFonts.load(readResourceBytes("Roboto-Medium.ttf"), "Roboto-Medium.ttf")
        val fonts = listOf("default" to UiFonts.default(), "Roboto-Medium.ttf" to loaded)
        for ((name, font) in fonts) {
            val glyphs = assertNotNull(font.pixelGlyphs, name)
            for (size in sizes) {
                for (char in "lI") {
                    val glyph = assertNotNull(glyphs.glyph(char.code, FontWeight.Normal, size))
                    val columns = columnPeaks(glyph)
                    val what = "$name '$char' at $size px: columns $columns"
                    assertEquals(1, columns.count { it >= SOLID }, what)
                    assertTrue(columns.count { it > FAINT } <= 2, what)
                }
            }
        }
    }

    /** The cap height and the x-height land on whole pixels, so a flat top is one solid row, not two grey ones. */
    @Test
    fun capAndXHeightsLandOnWholePixels() {
        val glyphs = UiFonts.default().pixelGlyphs!!
        for (size in sizes) {
            for (char in "HTEz") {
                val glyph = glyphs.glyph(char.code, FontWeight.Normal, size)!!
                val topRow = (0 until glyph.width).maxOf { alpha(glyph, it, 0) }
                assertTrue(topRow >= SOLID_ROW, "'$char' at $size px: its top row peaks at $topRow")
            }
        }
    }

    /** The baseline is a pixel boundary: a flat bottom ends on a solid row with nothing under it. */
    @Test
    fun flatBottomsEndOnTheBaseline() {
        val glyphs = UiFonts.default().pixelGlyphs!!
        for (size in sizes) {
            val glyph = glyphs.glyph('H'.code, FontWeight.Normal, size)!!
            assertEquals(0, glyph.top + glyph.height, "'H' at $size px ends ${glyph.top + glyph.height} px from the baseline")
            val bottomRow = (0 until glyph.width).maxOf { alpha(glyph, it, glyph.height - 1) }
            assertTrue(bottomRow >= SOLID_ROW, "'H' at $size px: its bottom row peaks at $bottomRow")
        }
    }

    /** A size's glyphs are rasterized once: the same glyph comes back, and one block shares one page. */
    @Test
    fun eachFontAndSizeRasterizesOnce() {
        val glyphs = UiFonts.trueSans().pixelGlyphs!!
        val first = glyphs.glyph('a'.code, FontWeight.Normal, 13)!!
        assertSame(first, glyphs.glyph('a'.code, FontWeight.Normal, 13))
        assertSame(first.atlas, glyphs.glyph('Z'.code, FontWeight.Normal, 13)!!.atlas)
        assertNotSame(first.atlas, glyphs.glyph('a'.code, FontWeight.Normal, 14)!!.atlas)
        assertEquals(UiFontSamplingMode.CoverageAlpha, first.atlas.samplingMode)
        assertTrue(first.atlas.linearCoverage)
        assertNull(glyphs.glyph('a'.code, FontWeight.Normal, 0))
        assertNull(glyphs.glyph('a'.code, FontWeight.Normal, PixelGlyphs.MAX_PIXEL_SIZE + 1))
    }

    /** A missing code point draws the font's stand-in, as the atlas does. */
    @Test
    fun aMissingCodePointDrawsTheStandIn() {
        val glyphs = UiFonts.trueSans().pixelGlyphs!!
        assertSame(glyphs.glyph('?'.code, FontWeight.Normal, 12), glyphs.glyph(0x0416, FontWeight.Normal, 12))
    }

    /** Each weight of the default family rasterizes its own face. */
    @Test
    fun weightsDrawTheirOwnFaces() {
        val glyphs = UiFonts.default().pixelGlyphs!!
        val regular = glyphs.glyph('H'.code, FontWeight.Normal, 12)!!
        val bold = glyphs.glyph('H'.code, FontWeight.Bold, 12)!!
        assertNotSame(regular.atlas, bold.atlas)
        assertTrue(ink(bold) > ink(regular) * 1.2, "bold 'H' (${ink(bold)}) is not heavier than regular (${ink(regular)})")
    }

    /** Fonts without outlines have no pixel glyphs; a limit of 0 opts a font out, and any limit keeps its atlas. */
    @Test
    fun theLimitAndTheOptOut() {
        assertNull(UiFonts.bitmap().pixelGlyphs)
        val default = UiFonts.default()
        assertEquals(UiFont.DEFAULT_PIXEL_GLYPH_MAX_SIZE, default.pixelGlyphMaxSize)
        val off = default.withPixelGlyphMaxSize(0)
        assertNull(off.pixelGlyphs)
        assertEquals(0, off.pixelGlyphMaxSize)
        val sixteen = off.withPixelGlyphMaxSize(16)
        assertEquals(16, sixteen.pixelGlyphMaxSize)
        assertSame(default.pixelGlyphs, sixteen.pixelGlyphs)
        assertSame(default, sixteen.fontFor('H'.code, FontWeight.Normal))
        assertSame(default.atlasPages.single(), sixteen.atlasPages.single())
        assertSame(default, default.withPixelGlyphMaxSize(UiFont.DEFAULT_PIXEL_GLYPH_MAX_SIZE))
        assertFailsWith<IllegalArgumentException> { default.withPixelGlyphMaxSize(-1) }
        assertNull(UiFonts.fallback(UiFonts.bitmap(), listOf(default)).pixelGlyphs)
        assertNotNull(UiFonts.fallback(default, listOf(UiFonts.bitmap())).pixelGlyphs)
    }

    /** A page's bytes, pinned so every target that runs this proves it rasterizes the same glyphs. */
    @Test
    fun everyTargetRasterizesTheSameBytes() {
        val page = UiFonts.trueSans().pixelGlyphs!!.glyph('H'.code, FontWeight.Normal, 12)!!.atlas
        var hash = FNV_OFFSET
        page.atlasPixelsRgba.forEach { hash = (hash xor (it.toLong() and 0xFF)) * FNV_PRIME }
        assertEquals(REGULAR_12PX_PAGE_HASH, hash, "page ${page.atlasWidth}x${page.atlasHeight}")
    }

    /** Each pixel's difference from [referenceCoverage] of the same outline. */
    private fun errorsAgainstReference(outline: List<OutlineSegment>, scale: Double, bitmap: GlyphBitmap): List<Double> {
        val curves = outline.map { segment ->
            val p = segment.points
            FieldCurve(DoubleArray(8) { if (it % 2 == 0) p[it] * scale else -p[it] * scale }, segment.isLine)
        }
        return (0 until bitmap.width * bitmap.height).map { index ->
            val column = index % bitmap.width
            val row = index / bitmap.width
            val actual = (bitmap.mask[index].toInt() and 0xFF) / 255.0
            abs(actual - referenceCoverage(curves, bitmap.left + column, bitmap.top + row))
        }
    }

    /** The share of a [GRID] by [GRID] grid of points in the pixel at ([x], [y]) that [curves] wind around. */
    private fun referenceCoverage(curves: List<FieldCurve>, x: Int, y: Int): Double {
        var inside = 0
        for (sample in 0 until GRID * GRID) {
            val px = x + (sample % GRID + 0.5) / GRID
            val py = y + (sample / GRID + 0.5) / GRID
            if (curves.sumOf { it.crossings(px, py) } != 0) inside++
        }
        return inside.toDouble() / (GRID * GRID)
    }

    private fun alpha(glyph: PixelGlyph, column: Int, row: Int): Int {
        val atlas = glyph.atlas
        val x = (glyph.u0 * atlas.atlasWidth + 0.5f).toInt() + column
        val y = (glyph.v0 * atlas.atlasHeight + 0.5f).toInt() + row
        return atlas.atlasPixelsRgba[(y * atlas.atlasWidth + x) * 4 + 3].toInt() and 0xFF
    }

    /** The glyph's total coverage. */
    private fun ink(glyph: PixelGlyph): Int = (0 until glyph.height).sumOf { row -> (0 until glyph.width).sumOf { alpha(glyph, it, row) } }

    /** Each column's strongest coverage, across the glyph's middle rows (clear of serifs and terminals). */
    private fun columnPeaks(glyph: PixelGlyph): List<Int> {
        val rows = (glyph.height / 4) until (glyph.height * 3 / 4).coerceAtLeast(glyph.height / 4 + 1)
        return (0 until glyph.width).map { column -> rows.maxOf { alpha(glyph, column, it) } }
    }

    private companion object {
        const val GRID = 16
        const val SAMPLING_TOLERANCE = 0.07
        const val WORST_TOLERANCE = 0.12
        const val SOLID = 250

        /** A row resolves to 1/16 of a pixel down the glyph, so a full one can read 15/16. */
        const val SOLID_ROW = 238
        const val FAINT = 12
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L
        const val REGULAR_12PX_PAGE_HASH = -3514475784191782169L
    }
}
