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
}
