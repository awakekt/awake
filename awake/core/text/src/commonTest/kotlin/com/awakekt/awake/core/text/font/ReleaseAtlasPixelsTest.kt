/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** A font lets its atlas pixels go once a renderer has uploaded them, and builds the same pixels again when asked (#1619). */
class ReleaseAtlasPixelsTest {
    @Test
    fun aPackedFontDecodesTheSamePixelsAgainAfterARelease() {
        val font = UiFonts.trueSans()
        val first = font.atlasPixelsRgba
        assertSame(first, font.atlasPixelsRgba, "the pixels are kept between reads until they are released")

        font.releaseAtlasPixels()

        val again = font.atlasPixelsRgba
        assertNotSame(first, again, "a release drops the decoded atlas")
        assertContentEquals(first, again)
    }

    @Test
    fun aWeightedFamilyStacksTheSamePixelsAgainAfterARelease() {
        val family = UiFonts.weightedSans()
        val first = family.atlasPixelsRgba

        family.releaseAtlasPixels()

        val again = family.atlasPixelsRgba
        assertNotSame(first, again, "a release drops the combined atlas")
        assertContentEquals(first, again)
    }

    @Test
    fun aFontWithNoOtherCopyOfItsPixelsKeepsThem() {
        val font = UiFonts.bitmap()
        val first = font.atlasPixelsRgba

        font.releaseAtlasPixels()

        assertSame(first, font.atlasPixelsRgba)
    }
}
