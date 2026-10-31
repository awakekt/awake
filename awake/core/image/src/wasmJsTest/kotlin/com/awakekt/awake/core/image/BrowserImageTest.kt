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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The image a GPU upload copies is the one [createBitmap] reads: [BrowserImage.toBitmap] gives the same
 * pixels bit for bit. Decoding one for the GPU makes no canvas at all, since nothing is read back.
 * The WebGPU backend's `ExternalImageUploadBrowserTest` checks what the GPU copy holds.
 *
 * Negative control, measured: decoding with `imageOrientation: 'flipY'` (the flip done by the browser,
 * as the issue first proposed) fails the first golden at pixel 0 (0xff00000b, expected 0xfffffd06),
 * because [BrowserImage.toBitmap] shares [createBitmap]'s readback, which flips the rows itself.
 */
class BrowserImageTest {
    @Test
    fun readsBackWhatCreateBitmapDecodes() = runTest(timeout = 2.minutes) {
        for (image in PngFixtures.all) {
            decodeBrowserImage(image.png).use { decoded ->
                assertEquals(image.width, decoded.width, image.name)
                assertEquals(image.height, decoded.height, image.name)
                assertContentEquals(createBitmap(image.png).pixels, decoded.toBitmap().pixels, image.name)
            }
        }
    }

    @Test
    fun decodesForTheGpuWithoutACanvas() = runTest(timeout = 2.minutes) {
        countCanvasCreation()
        val before = canvasesCreated()
        repeat(3) { decodeBrowserImage(PngFixtures.opaque256.png).close() }
        assertEquals(before, canvasesCreated())
    }

    @Test
    fun aClosedImageRefusesItsPixels() = runTest {
        val image = decodeBrowserImage(PngFixtures.alphaOddWidth.png)
        assertFalse(image.isClosed)
        image.close()
        image.close()
        assertTrue(image.isClosed)
        assertFailsWith<IllegalStateException> { image.toBitmap() }
        assertFailsWith<IllegalStateException> { image.imageBitmap }
        assertEquals(255 to 3, image.width to image.height, "the size stays readable")
    }
}
