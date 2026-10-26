/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.unicode.CodePoints
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The built-in font, and a font [UiFonts.load] bakes with no chars named, draw accented letters and common signs
 * as themselves and not as the '?' that stands in for a char a font lacks (#1058).
 */
class LatinCoverageTest {
    private val weights = listOf(
        FontWeight.Thin,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.Black,
    )

    /** A sample the issue names, and a few of Latin Extended-A. */
    private val sample = "ÉÅÑÇÿ€™©ŁŚŽőű"

    @Test
    fun theDefaultSetIsLatin1LatinExtendedAAndTheQuotesAndSigns() {
        val codes = codePoints(UiFonts.DEFAULT_CHARS)
        assertEquals(codes.size, codes.toSet().size, "a char is listed twice")
        assertEquals(339, codes.size)
        val wanted = (0x20..0x7E) + (0xA0..0xFF) + (0x100..0x17F) +
            "‘’‚“”„†‡‰′″‹›€™−•…–—".map { it.code }
        assertEquals(emptyList(), wanted.filter { it !in codes }.map { it.toString(16) }, "wanted but not in the set")
        assertEquals(emptyList(), codes.filter { it !in wanted }.map { it.toString(16) }, "in the set but not wanted")
        assertTrue(sample.all { it in UiFonts.DEFAULT_CHARS })
    }

    @Test
    fun theAsciiSetIsTheSmallerOldDefault() {
        assertEquals(102, codePoints(UiFonts.ASCII_CHARS).size)
        assertTrue(UiFonts.DEFAULT_CHARS.startsWith(UiFonts.ASCII_CHARS), "the default set extends the ASCII set")
    }

    @Test
    fun theBuiltInFontDrawsEveryDefaultCharInEveryWeight() {
        val font = UiFonts.weightedSans()
        weights.forEach { weight -> assertDrawsEveryDefaultChar(font, weight, "built-in $weight") }
    }

    @Test
    fun aDefaultLoadDrawsEveryDefaultChar() = runTest {
        val font = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf")
        assertDrawsEveryDefaultChar(font, FontWeight.Normal, "UiFonts.load")
    }

    @Test
    fun asciiCharsLoadASmallerAtlasThatDrawsAccentsAsTheStandIn() = runTest {
        val bytes = readResourceBytes("Roboto-Regular.ttf")
        val ascii = UiFonts.load(bytes, "Roboto-Regular.ttf", UiFonts.ASCII_CHARS)
        val wide = UiFonts.load(bytes, "Roboto-Regular.ttf")
        assertTrue(ascii.atlasWidth.toLong() * ascii.atlasHeight * 2 < wide.atlasWidth.toLong() * wide.atlasHeight, "the ASCII atlas is the smaller")
        assertEquals(ascii.glyphFor('?'.code, FontWeight.Normal), ascii.glyphFor('É'.code, FontWeight.Normal), "É is the stand-in")
        assertNotEquals(wide.glyphFor('?'.code, FontWeight.Normal), wide.glyphFor('É'.code, FontWeight.Normal))
    }

    /** The glyph's ink sits inside the quad that is drawn, so a clip the size of the glyph's box cannot cut it (#1053). */
    @Test
    fun everyDefaultCharsInkIsInsideItsQuad() = runTest {
        val loaded = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf") as PackedUiFont
        for ((label, font) in listOf("built-in" to UiFonts.trueSans() as PackedUiFont, "loaded" to loaded)) {
            val inked = codePoints(UiFonts.DEFAULT_CHARS).filter { code -> font.inkFor(Char(code)).let { it != null && it.widthEm > 0f && it.heightEm > 0f } }
            assertTrue(inked.size > 300, "$label has ${inked.size} inked glyphs")
            for (code in inked) {
                val ink = font.inkFor(Char(code))!!
                val quad = font.glyphFor(code, FontWeight.Normal)!!
                val hex = code.toString(16)
                assertTrue(quad.offsetXEm <= ink.offsetXEm + EPSILON && quad.offsetYEm <= ink.offsetYEm + EPSILON, "$label U+$hex ink starts outside its quad")
                assertTrue(
                    quad.offsetXEm + quad.widthEm >= ink.offsetXEm + ink.widthEm - EPSILON &&
                        quad.offsetYEm + quad.heightEm >= ink.offsetYEm + ink.heightEm - EPSILON,
                    "$label U+$hex ink ends outside its quad",
                )
            }
        }
    }

    /** Accents on capitals reach above the cap height but stay inside the line box the font is laid out in (#1024). */
    @Test
    fun accentedCapitalsStayInsideTheLineBox() = runTest {
        val loaded = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf") as PackedUiFont
        for ((label, font) in listOf("built-in" to UiFonts.trueSans() as PackedUiFont, "loaded" to loaded)) {
            for (char in "ÀÁÂÃÄÅÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝĀĂĆČĎĚĞŃŇŐŘŚŠŤŮŰŹŻŽ") {
                val ink = font.inkFor(char)!!
                val aboveBaseline = font.ascentEm - ink.offsetYEm
                assertTrue(aboveBaseline > font.capHeightEm, "$label $char has no accent above the cap height")
                assertTrue(aboveBaseline <= font.lineAscentEm, "$label $char reaches $aboveBaseline em above the baseline, past the line box's ${font.lineAscentEm}")
            }
        }
    }

    /** Adding the Latin sets must not move the descent every line of text keeps its ink inside of (Ģ and § hang below Roboto's descenders). */
    @Test
    fun theWiderSetDoesNotMoveTheInkBand() = runTest {
        val bytes = readResourceBytes("Roboto-Regular.ttf")
        val wide = UiFonts.load(bytes, "Roboto-Regular.ttf") as PackedUiFont
        val ascii = UiFonts.load(bytes, "Roboto-Regular.ttf", UiFonts.ASCII_CHARS) as PackedUiFont
        assertEquals(ascii.descentEm, wide.descentEm)
        assertEquals(ascii.visibleTopEm, wide.visibleTopEm)
        assertEquals(ascii.visibleBottomEm, wide.visibleBottomEm)
        assertEquals(0.22949207f, (UiFonts.trueSans() as PackedUiFont).descentEm)
    }

    private fun assertDrawsEveryDefaultChar(font: UiFont, weight: FontWeight, label: String) {
        val standIn = font.glyphFor(NEVER_MAPPED, weight)
        val standInPixels = font.pixelGlyphs?.glyph(NEVER_MAPPED, weight, PIXEL_SIZE)
        val lost = codePoints(UiFonts.DEFAULT_CHARS).filter { code ->
            code != '?'.code && (font.glyphFor(code, weight) == standIn || font.pixelGlyphs?.glyph(code, weight, PIXEL_SIZE) === standInPixels)
        }
        assertEquals(emptyList(), lost.map { "U+" + it.toString(16) }, "$label draws these as '?'")
        assertTrue(sample.all { font.hasGlyph(it) }, "$label lacks a char of $sample")
    }

    private fun codePoints(text: String): List<Int> {
        val codes = ArrayList<Int>()
        CodePoints.forEach(text) { _, code -> codes += code }
        return codes
    }

    private companion object {
        /** A noncharacter no font maps, so a lookup for it returns the stand-in. */
        const val NEVER_MAPPED = 0xFFFF
        const val PIXEL_SIZE = 12
        const val EPSILON = 1e-4f
    }
}
