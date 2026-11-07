/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.assets

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AssetImagesTest {
    private val files = mapOf("ui/frame.png" to byteArrayOf(1, 2, 3, 4), "ui/icon.png" to byteArrayOf(5, 6, 7, 8))
    private val reads = mutableListOf<String>()
    private var decodes = 0

    private val source = AssetSource { path ->
        reads += path.value
        files[path.value]?.let { Result.success(it) } ?: Result.failure(NoSuchElementException("no file ${path.value}"))
    }

    // A one-pixel image of the file's first four bytes: decoding is the platform's, tested on its own.
    private fun images(scope: CoroutineScope) = AssetImages(source, scope) { bytes ->
        decodes++
        ImageBitmap(1, 1, bytes.copyOf(ImageBitmap.BYTES_PER_PIXEL))
    }

    private fun TestScope.cache() = images(backgroundScope)

    @Test
    fun anImageIsNullUntilItLoadsAndThenTheDecodedOne() = runTest {
        val images = cache()

        assertNull(images["ui/frame.png"], "nothing is decoded on the frame that asks")
        assertTrue(images.state("ui/frame.png").isLoading)
        runCurrent()

        val frame = assertNotNull(images["ui/frame.png"])
        assertEquals(0x01020304, frame.pixel(0, 0))
        assertTrue(!images.state("ui/frame.png").isLoading)
    }

    @Test
    fun askingAgainReadsAndDecodesNothingMore() = runTest {
        val images = cache()
        images["ui/frame.png"]
        images["ui/frame.png"]
        runCurrent()

        val first = images["ui/frame.png"]
        repeat(10) { assertSame(first, images["ui/frame.png"]) }
        assertEquals(listOf("ui/frame.png"), reads)
        assertEquals(1, decodes)
    }

    @Test
    fun aResetFreesEveryImageAndTheNextAskLoadsAgain() = runTest {
        val images = cache()
        images["ui/frame.png"]
        runCurrent()
        val before = images["ui/frame.png"]

        images.reset()

        assertEquals(0, images.size)
        assertNull(images["ui/frame.png"])
        runCurrent()
        assertNotSame(before, images["ui/frame.png"])
        assertEquals(2, decodes)
    }

    @Test
    fun aLoadStillRunningAtAResetNeverLandsInTheNextScene() = runTest {
        val images = cache()
        val leaving = images.state("ui/frame.png")

        images.reset()
        runCurrent()

        assertNull(leaving.value, "the cancelled load wrote nothing")
        assertEquals(0, images.size)
        assertEquals(0, decodes)
    }

    @Test
    fun aMissingPathStaysNullIsTriedOnceAndSaysWhy() = runTest {
        val images = cache()

        repeat(3) { assertNull(images["ui/missing.png"]) }
        runCurrent()
        repeat(3) { assertNull(images["ui/missing.png"]) }

        assertIs<NoSuchElementException>(images.failure("ui/missing.png"))
        assertEquals(listOf("ui/missing.png"), reads)
        assertTrue(!images.state("ui/missing.png").isLoading)
    }

    @Test
    fun aDecoderThatThrowsIsAFailureNotACrash() = runTest {
        val images = AssetImages(source, backgroundScope) { error("not an image") }

        images["ui/frame.png"]
        runCurrent()

        assertNull(images["ui/frame.png"])
        assertIs<IllegalStateException>(images.failure("ui/frame.png"))
    }

    @Test
    fun theCanvasImagesASceneSeedsAreSharedNotDecodedAgain() = runTest {
        val images = cache()
        val canvasFrame = ImageBitmap(1, 1, byteArrayOf(9, 9, 9, 9))

        images.reset(mapOf("ui/frame.png" to canvasFrame))

        assertSame(canvasFrame, images["ui/frame.png"], "returned on the first ask, with no load")
        runCurrent()
        assertSame(canvasFrame, images["ui/frame.png"])
        assertEquals(emptyList(), reads)
        assertEquals(0, decodes)
    }
}
