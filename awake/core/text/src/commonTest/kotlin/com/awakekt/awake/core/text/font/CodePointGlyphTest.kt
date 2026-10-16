/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `hasGlyph(codePoint)` and the code point lookups, for characters past U+FFFF that a `Char` cannot hold. */
class CodePointGlyphTest {

    /** A packed font holding '?', 'A' and U+20000, a CJK Extension B ideograph stored as a surrogate pair. */
    private val packed = PackedUiFont(FakeFontData("?A$EXT_B"))

    @Test
    fun aPackedFontFindsAGlyphPastTheBasicPlane() {
        assertTrue(packed.hasGlyph(EXT_B_CODE))
        assertTrue(packed.hasGlyph('A'.code))
        assertTrue(packed.hasGlyph('A'))
        assertFalse(packed.hasGlyph(0x1F600))
        // Neither half of the pair is a glyph on its own.
        assertFalse(packed.hasGlyph(EXT_B[0]))
        assertFalse(packed.hasGlyph(EXT_B[0].code))
        val glyph = assertNotNull(packed.glyphFor(EXT_B_CODE, FontWeight.Normal))
        assertEquals(EXT_B_U0, glyph.u0)
        assertEquals(EXT_B_ADVANCE * 10f, packed.advanceFor(EXT_B_CODE, 10f, FontWeight.Normal))
        // A missing code point draws the stand-in.
        assertEquals(packed.uvFor('?'), packed.glyphFor(0x1F600, FontWeight.Normal))
    }

    @Test
    fun theDefaultLooksBasicPlaneCodePointsUpAsChars() {
        val font = UiFonts.trueSans()
        assertTrue(font.hasGlyph('A'.code))
        assertFalse(font.hasGlyph(0x1F600))
        assertFalse(font.hasGlyph(0xD83D))
        assertFalse(font.hasGlyph(-1))
        assertEquals(font.uvFor('A'), font.glyphFor('A'.code, FontWeight.Normal))
        assertEquals(font.advanceFor('A', 12f), font.advanceFor('A'.code, 12f, FontWeight.Normal))
    }

    @Test
    fun aFallbackChainResolvesFacesByCodePoint() {
        val primary = UiFonts.trueSans()
        val chain = UiFonts.fallback(primary, listOf(packed))
        assertTrue(chain.hasGlyph(EXT_B_CODE))
        assertFalse(chain.hasGlyph(0x1F600))
        assertTrue(chain.hasGlyph('A'.code))
        assertEquals(packed.advanceFor(EXT_B_CODE, 16f, FontWeight.Normal), chain.advanceFor(EXT_B_CODE, 16f, FontWeight.Normal))
        val glyph = assertNotNull(chain.glyphFor(EXT_B_CODE, FontWeight.Normal))
        val primarySlice = primary.atlasHeight.toFloat() / chain.atlasHeight
        assertTrue(glyph.v0 >= primarySlice - 1e-6f, "U+20000 samples the fallback's slice")
    }

    @Test
    fun aWeightedFamilyAsksItsFacesByCodePoint() {
        val family = UiFonts.family(mapOf(FontWeight.Normal to packed, FontWeight.Bold to UiFonts.trueSans()))
        assertTrue(family.hasGlyph(EXT_B_CODE))
        assertNotNull(family.glyphFor(EXT_B_CODE, FontWeight.Normal))
    }

    @Test
    fun aLoadedFontBakesCodePointsNotHalfPairs() = runTest {
        // The subset has 'H' but nothing past U+FFFF: the pair must neither fail nor bake its halves.
        val font = UiFonts.load(readResourceBytes("fonts/roboto-subset.ttf"), "ttf", chars = "H$EXT_B")
        assertTrue(font.hasGlyph('H'.code))
        assertFalse(font.hasGlyph(EXT_B_CODE))
        assertFalse(font.hasGlyph(EXT_B[0]))
    }

    private class FakeFontData(override val glyphOrder: String) : PackedUiFontData {
        private val glyphs = glyphOrder.codePointCount()
        override val name = "fake"
        override val baseCellSize = 16
        override val lineHeightEm = 1.2f
        override val textScaleStep = 0.25f
        override val atlasWidth = 64
        override val atlasHeight = 64
        override val samplingMode = UiFontSamplingMode.DistanceField
        override val fallbackChar = '?'
        override val encodedAtlasBase64 = ""
        override val uvBoundsPx = IntArray(glyphs * 4) { index -> if (index == 8) EXT_B_U0_PX else index }
        override val quadMetricsEm = FloatArray(glyphs * 4) { 0.5f }
        override val advancesEm = FloatArray(glyphs) { index -> if (index == 2) EXT_B_ADVANCE else 0.5f }

        override fun atlasBytes(): ByteArray = ByteArray(atlasWidth * atlasHeight)

        private fun String.codePointCount(): Int {
            var count = 0
            var index = 0
            while (index < length) {
                index += if (this[index].isHighSurrogate()) 2 else 1
                count++
            }
            return count
        }
    }

    private companion object {
        const val EXT_B = "\uD840\uDC00"
        const val EXT_B_CODE = 0x20000
        const val EXT_B_U0_PX = 32
        const val EXT_B_U0 = 0.5f
        const val EXT_B_ADVANCE = 1.5f
    }
}
