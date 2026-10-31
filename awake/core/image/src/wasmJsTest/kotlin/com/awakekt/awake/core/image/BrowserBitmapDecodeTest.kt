/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * The browser decode returns the pixels the files hold, bottom row first, as the desktop decode
 * does (`DesktopBitmapDecodeTest` checks the same fixtures there), bit-identical to the decode it
 * replaced, and without a `<canvas>` per texture. `BrowserBitmapDecodeBenchmark` times it.
 *
 * The bit-for-bit comparison holds where both decodes draw on a CPU canvas, as headless Chrome's
 * default launcher (`--disable-gpu`) gives them. On a GPU-accelerated canvas the old decode's own
 * premultiply rounding differs (0xcc1ea308 against 0xcc1ea208 for one translucent pixel, under
 * SwiftShader), so "today's" translucent colour already depended on the browser's canvas backend.
 *
 * Negative controls, measured on headless Chromium: dropping the row flip fails every golden at
 * pixel 0 (0xff00000b, expected 0xfffffd06); swapping the R and B shifts fails them at pixel 0
 * (0xfd0600ff); not clearing the shared canvas between same-size decodes fails the bit-for-bit test
 * (0xff0afa01, expected 0x04ff4000). Leaving out `premultiplyAlpha: 'none'` changes nothing: the 2D
 * canvas premultiplies either way, as it did for the old decode.
 */
class BrowserBitmapDecodeTest {
    @Test
    fun opaqueImagesDecodeToTheirExactPixels() = runTest(timeout = TIMEOUT) {
        for (image in PngFixtures.all.filter { it.opaque }) {
            assertPixels(image, image.expectedBottomUpArgb(), createBitmap(image.png))
        }
    }

    // A 2D canvas stores premultiplied colour, so a translucent pixel's RGB comes back rounded, as it
    // always has here; alpha is exact, and RGB is within that rounding of the file's value.
    @Test
    fun translucentImagesKeepAlphaAndStraightColour() = runTest(timeout = TIMEOUT) {
        for (image in PngFixtures.all.filterNot { it.opaque }) {
            val decoded = createBitmap(image.png)
            assertEquals(image.width, decoded.width, image.name)
            assertEquals(image.height, decoded.height, image.name)
            val expected = image.expectedBottomUpArgb()
            for (i in expected.indices) {
                val want = expected[i]
                val got = decoded.pixels[i]
                val alpha = want ushr 24
                assertEquals(alpha, got ushr 24, "${image.name} alpha at pixel $i")
                if (alpha == 0) continue
                val tolerance = 255 / alpha + 1
                for (shift in intArrayOf(16, 8, 0)) {
                    val delta = ((want shr shift) and 0xFF) - ((got shr shift) and 0xFF)
                    if (delta !in -tolerance..tolerance) fail("${image.name} pixel $i: ${hex(got)}, file holds ${hex(want)}")
                }
            }
        }
    }

    @Test
    fun matchesThePerElementDecodeBitForBit() = runTest(timeout = TIMEOUT) {
        for (image in PngFixtures.all) {
            assertPixels(image, perElementCanvasDecode(image.png).pixels, createBitmap(image.png))
        }
    }

    // Negative control: the per-element decode in place of createBitmap here makes 3 canvases, not 0.
    @Test
    fun decodesWithoutACanvasPerTexture() = runTest(timeout = TIMEOUT) {
        countCanvasCreation()
        createBitmap(PngFixtures.alphaOddWidth.png) // The shared context may be made on first use.
        val before = canvasesCreated()
        repeat(3) { createBitmap(PngFixtures.opaque256.png) }
        assertEquals(before, canvasesCreated())
    }

    private fun assertPixels(image: PngFixtures.Image, expected: IntArray, decoded: Bitmap) {
        assertEquals(image.width, decoded.width, image.name)
        assertEquals(image.height, decoded.height, image.name)
        assertEquals(4, decoded.channel, image.name)
        val first = expected.indices.firstOrNull { expected[it] != decoded.pixels[it] } ?: return
        fail("${image.name} pixel $first (x ${first % image.width}, row ${first / image.width} from the bottom): ${hex(decoded.pixels[first])}, expected ${hex(expected[first])}")
    }

    private fun hex(argb: Int): String = "0x" + argb.toUInt().toString(16).padStart(8, '0')

    private companion object {
        val TIMEOUT = 2.minutes
    }
}
