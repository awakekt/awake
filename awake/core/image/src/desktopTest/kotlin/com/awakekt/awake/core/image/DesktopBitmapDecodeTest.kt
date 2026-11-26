/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * The desktop decode reads every fixture to exactly the pixels it holds, bottom row first: the
 * reference the browser decode is held to in `BrowserBitmapDecodeTest`, which also proves the
 * generated PNGs are valid files a stock decoder reads.
 */
class DesktopBitmapDecodeTest {
    @Test
    fun everyFixtureDecodesToItsExactPixels() {
        for (image in PngFixtures.all) {
            val decoded = decodeBitmap(image.png)
            assertEquals(image.width, decoded.width, image.name)
            assertEquals(image.height, decoded.height, image.name)
            assertContentEquals(image.expectedBottomUpArgb(), decoded.pixels, image.name)
        }
    }

    @Test
    fun decodeImageWithMipSkipDownsamplesAndMatchesFullDecodeAtZero() = runTest {
        val full = decodeImage(PngFixtures.alpha256.png, mipSkip = 0)
        assertEquals(256, full.width)
        assertEquals(256, full.height)
        assertContentEquals(PngFixtures.alpha256.expectedBottomUpArgb(), full.toBitmap().pixels)
        assertContentEquals(full.toBitmap().toRgba8Bytes(), full.toRgba8Bytes())

        val half = decodeImage(PngFixtures.opaque256.png, mipSkip = 1)
        assertEquals(128, half.width)
        assertEquals(128, half.height)

        val quarter = decodeImageBlocking(PngFixtures.opaque256.png, mipSkip = 2)
        assertEquals(64, quarter.width)
        assertEquals(64, quarter.height)
    }
}
