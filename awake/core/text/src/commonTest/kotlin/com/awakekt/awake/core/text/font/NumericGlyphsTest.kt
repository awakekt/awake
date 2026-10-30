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
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NumericGlyphsTest {
    @Test
    fun robotoNumeralsFixtureSubstitutesTabularDigitsThroughFormatsOneTwoAndExtension() = runTest {
        val bytes = readResourceBytes("fonts/roboto-numerals.ttf")
        val loaded = UiFonts.load(bytes, "roboto-numerals.ttf", chars = " ?0123456789:")

        assertSame(loaded, loaded.withNumerals(null))
        assertEquals(889f / 2048f * 16f, loaded.advanceFor('1', 16f), 1e-4f)
        assertEquals(1191f / 2048f * 16f, loaded.advanceFor('0', 16f), 1e-4f)
        assertTrue(loaded.advanceFor('1', 16f) < loaded.advanceFor('0', 16f))

        val tabular = loaded.withNumerals(FontNumerals.Tabular)
        assertSame(tabular, loaded.withNumerals(FontNumerals.Tabular))
        assertSame(tabular, tabular.withNumerals(FontNumerals.Tabular))
        val expectedAdvance = 1151f / 2048f * 16f
        for (digit in '0'..'9') {
            assertEquals(expectedAdvance, tabular.advanceFor(digit, 16f), 1e-5f, "tabular '$digit'")
        }
        assertNotEquals(loaded.uvFor('1'), tabular.uvFor('1'), "distance-field '1' swaps to its tabular glyph")
        val defaultPixelOne = checkNotNull(loaded.pixelGlyphs?.glyph('1'.code, FontWeight.Normal, 24))
        val tabularPixelOne = checkNotNull(tabular.pixelGlyphs?.glyph('1'.code, FontWeight.Normal, 24))
        assertTrue(defaultPixelOne.atlas !== tabularPixelOne.atlas)
        assertTrue(
            tabularPixelOne.left > defaultPixelOne.left,
            "tabular '1' pixel glyph sits wider in its equal-width cell (${tabularPixelOne.left} vs ${defaultPixelOne.left})",
        )
        assertTrue(tabular.atlasPixelsRgba.isNotEmpty())
    }

    @Test
    fun builtInRobotoFacesAdvanceEveryDigitEquallyUnderTabularAndNarrowlyUnderProportional() {
        val family = UiFonts.default()
        val singleFace = UiFonts.trueSans()
        val bitmap = UiFonts.bitmap()

        assertSame(family, family.withNumerals(null))
        assertSame(singleFace, singleFace.withNumerals(null))
        assertSame(bitmap, bitmap.withNumerals(FontNumerals.Tabular))

        val tabular = family.withNumerals(FontNumerals.Tabular)
        val proportional = family.withNumerals(FontNumerals.Proportional)
        assertSame(tabular, family.withNumerals(FontNumerals.Tabular))
        assertSame(tabular, tabular.withNumerals(FontNumerals.Tabular))
        assertSame(proportional, tabular.withNumerals(FontNumerals.Proportional))

        val weights = listOf(
            FontWeight.Thin,
            FontWeight.Light,
            FontWeight.Normal,
            FontWeight.Medium,
            FontWeight.SemiBold,
            FontWeight.Bold,
            FontWeight.Black,
        )
        for (weight in weights) {
            val zeroTabular = tabular.advanceFor('0'.code, 16f, weight)
            for (digit in '1'..'9') {
                assertEquals(zeroTabular, tabular.advanceFor(digit.code, 16f, weight), "tabular '$digit' in $weight")
            }
            val zeroProp = proportional.advanceFor('0'.code, 16f, weight)
            val oneProp = proportional.advanceFor('1'.code, 16f, weight)
            assertTrue(oneProp < zeroProp, "proportional '1' ($oneProp) is narrower than '0' ($zeroProp) in $weight")
            assertNotEquals(
                tabular.glyphFor('1'.code, weight),
                proportional.glyphFor('1'.code, weight),
                "distance-field glyph for '1' differs in $weight",
            )
            val tabPx = checkNotNull(tabular.pixelGlyphs?.glyph('1'.code, weight, 14))
            val propPx = checkNotNull(proportional.pixelGlyphs?.glyph('1'.code, weight, 14))
            assertTrue(propPx.width > 0 && tabPx.width > 0)
        }

        val fallback = UiFonts.fallback(singleFace, listOf(bitmap))
        val fallbackProp = fallback.withNumerals(FontNumerals.Proportional)
        assertSame(fallbackProp, fallback.withNumerals(FontNumerals.Proportional))
        assertSame(fallbackProp, fallbackProp.withNumerals(FontNumerals.Proportional))
        assertTrue(fallbackProp.advanceFor('1', 16f) < fallbackProp.advanceFor('0', 16f))

        val limited = family.withPixelGlyphMaxSize(0)
        val limitedProp = limited.withNumerals(FontNumerals.Proportional)
        assertSame(limitedProp, limited.withNumerals(FontNumerals.Proportional))
        assertSame(limitedProp, limitedProp.withNumerals(FontNumerals.Proportional))
        assertEquals(0, limitedProp.pixelGlyphMaxSize)
        assertTrue(limitedProp.advanceFor('1', 16f) < limitedProp.advanceFor('0', 16f))
    }

    @Test
    fun corruptGsubTableIsRejectedNamingTheTable() = runTest {
        val original = readResourceBytes("fonts/roboto-numerals.ttf")
        val gsubOffset = tableOffset(original, "GSUB")

        // Corrupt LookupListOffset inside the GSUB header so it points past the table end.
        val badHeader = original.copyOf()
        badHeader[gsubOffset + 8] = 0x7F
        badHeader[gsubOffset + 9] = 0xFF.toByte()
        val error = runCatching { UiFonts.load(badHeader, "corrupt-gsub.ttf") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "Expected IllegalArgumentException, got $error")
        assertTrue("'GSUB' table" in error.message.orEmpty(), "Error should name 'GSUB' table: ${error.message}")
    }

    private fun tableOffset(bytes: ByteArray, tag: String): Int {
        fun u16(at: Int) = (bytes[at].toInt() and 0xFF) shl 8 or (bytes[at + 1].toInt() and 0xFF)
        val count = u16(4)
        val record = (0 until count).map { 12 + 16 * it }.single { at ->
            (0..3).all { bytes[at + it] == tag[it].code.toByte() }
        }
        return u16(record + 8) shl 16 or u16(record + 10)
    }
}
