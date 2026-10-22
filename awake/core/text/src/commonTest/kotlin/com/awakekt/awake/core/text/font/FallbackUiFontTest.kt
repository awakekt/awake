/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.host.readResourceBytes
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FallbackUiFontTest {

    private val cyrillic = "Привет мир"

    @Test
    fun aCharThePrimaryLacksDrawsFromTheFallback() = runTest {
        val primary = UiFonts.trueSans()
        val fallback = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf", chars = cyrillic)
        val chain = UiFonts.fallback(primary, listOf(fallback))

        assertFalse(primary.hasGlyph('П'))
        assertTrue(fallback.hasGlyph('П'))
        assertTrue(chain.hasGlyph('П'))
        assertTrue(chain.hasGlyph('A'))
        assertFalse(chain.hasGlyph('字'))

        assertEquals(primary.atlasHeight + fallback.atlasHeight, chain.atlasHeight)
        val primarySlice = primary.atlasHeight.toFloat() / chain.atlasHeight
        val latin = assertNotNull(chain.uvFor('A'))
        assertTrue(latin.v1 <= primarySlice + 1e-6f, "A samples the primary's slice")
        val cyr = assertNotNull(chain.uvFor('П'))
        assertTrue(cyr.v0 >= primarySlice - 1e-6f, "П samples the fallback's slice")
        assertEquals(fallback.advanceFor('П', 16f), chain.advanceFor('П', 16f))
        assertEquals(primary.lineHeightEm, chain.lineHeightEm)
    }

    @Test
    fun aCharNoFaceHasDrawsThePrimarysStandIn() = runTest {
        val primary = UiFonts.trueSans()
        val fallback = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf", chars = cyrillic)
        val chain = UiFonts.fallback(primary, listOf(fallback))
        val standIn = assertNotNull(primary.uvFor('字'))
        val drawn = assertNotNull(chain.uvFor('字'))
        assertEquals(standIn.u0 * primary.atlasWidth / chain.atlasWidth, drawn.u0)
    }

    @Test
    fun theCombinedAtlasStacksTheFaces() = runTest {
        val primary = UiFonts.trueSans()
        val fallback = UiFonts.load(readResourceBytes("Roboto-Regular.ttf"), "Roboto-Regular.ttf", chars = cyrillic)
        val chain = UiFonts.fallback(primary, listOf(fallback))
        val rowBytes = chain.atlasWidth * 4
        val firstFallbackRow = chain.atlasPixelsRgba.copyOfRange(primary.atlasHeight * rowBytes, primary.atlasHeight * rowBytes + fallback.atlasWidth * 4)
        assertContentEquals(fallback.atlasPixelsRgba.copyOfRange(0, fallback.atlasWidth * 4), firstFallbackRow)
    }

    @Test
    fun noFallbacksIsThePrimaryItself() {
        val primary = UiFonts.trueSans()
        assertSame(primary, UiFonts.fallback(primary, emptyList()))
    }

    @Test
    fun pageSplittingWhenAtlasExceedsMaxHeight() {
        val font1 = StubUiFont(atlasHeight = 60, charCodes = setOf('A'.code))
        val font2 = StubUiFont(atlasHeight = 50, charCodes = setOf('B'.code))
        val chain = UiFonts.fallback(font1, listOf(font2), maxAtlasHeight = 100)

        assertEquals(2, chain.atlasPages.size)
        val page0 = chain.atlasPages[0]
        val page1 = chain.atlasPages[1]
        assertEquals(60, page0.atlasHeight)
        assertEquals(50, page1.atlasHeight)

        val glyphA = assertNotNull(chain.glyphFor('A', FontWeight.Normal))
        assertEquals(0, glyphA.page)
        assertSame(page0, chain.fontFor('A'.code, FontWeight.Normal))

        val glyphB = assertNotNull(chain.glyphFor('B', FontWeight.Normal))
        assertEquals(1, glyphB.page)
        assertSame(page1, chain.fontFor('B'.code, FontWeight.Normal))

        assertSame(page0, chain.pageFont(0))
        assertSame(page1, chain.pageFont(1))
        assertSame(chain, chain.pageFont(99))
    }

    @Test
    fun fallbackChainOver8192PxSplitsAcrossPages() {
        val font1 = StubUiFont(atlasHeight = 5000, charCodes = setOf('A'.code))
        val font2 = StubUiFont(atlasHeight = 4000, charCodes = setOf('B'.code))
        // 5000 + 4000 = 9000 > default maxAtlasHeight (8192)
        val chain = UiFonts.fallback(font1, listOf(font2))

        assertEquals(2, chain.atlasPages.size)
        val page0 = chain.atlasPages[0]
        val page1 = chain.atlasPages[1]
        assertEquals(5000, page0.atlasHeight)
        assertEquals(4000, page1.atlasHeight)
        assertTrue(page0.atlasHeight <= UiFonts.DEFAULT_MAX_ATLAS_HEIGHT)
        assertTrue(page1.atlasHeight <= UiFonts.DEFAULT_MAX_ATLAS_HEIGHT)

        val glyphA = assertNotNull(chain.glyphFor('A', FontWeight.Normal))
        assertEquals(0, glyphA.page)
        assertEquals(0f, glyphA.v0)
        assertEquals(1f, glyphA.v1)

        val glyphB = assertNotNull(chain.glyphFor('B', FontWeight.Normal))
        assertEquals(1, glyphB.page)
        assertEquals(0f, glyphB.v0)
        assertEquals(1f, glyphB.v1)
    }

    @Test
    fun disparateSamplingModeFacesRemainDistinctPages() {
        val font1 = StubUiFont(atlasHeight = 50, charCodes = setOf('A'.code))
        val fontColor = StubUiFont(
            atlasHeight = 30,
            charCodes = setOf(0x1F600),
            samplingMode = UiFontSamplingMode.ColorRgba,
        )
        val chain = UiFonts.fallback(font1, listOf(fontColor))
        assertEquals(2, chain.atlasPages.size)
        assertSame(chain, chain.atlasPages[0])
        assertSame(fontColor, chain.atlasPages[1])
        assertSame(chain, chain.fontFor('A'.code, FontWeight.Normal))
        assertSame(fontColor, chain.fontFor(0x1F600, FontWeight.Normal))
    }
}

private class StubUiFont(
    override val atlasWidth: Int = 100,
    override val atlasHeight: Int = 50,
    private val charCodes: Set<Int> = emptySet(),
    override val samplingMode: UiFontSamplingMode = UiFontSamplingMode.DistanceField,
) : UiFont {
    override val cellSize: Int = 12
    override val textScaleStep: Float = 1f
    override val atlasPixelsRgba: ByteArray = ByteArray(atlasWidth * atlasHeight * 4)

    override fun uvFor(char: Char): GlyphRect? =
        if (char.code in charCodes) GlyphRect(0f, 0f, 1f, 1f) else null

    override fun hasGlyph(codePoint: Int): Boolean = codePoint in charCodes
    override fun hasGlyph(char: Char): Boolean = char.code in charCodes
    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = uvFor(char)
    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? =
        if (codePoint in charCodes) GlyphRect(0f, 0f, 1f, 1f) else null
}
