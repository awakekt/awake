/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

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
}
