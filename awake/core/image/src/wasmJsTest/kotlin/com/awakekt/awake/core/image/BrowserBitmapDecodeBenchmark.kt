/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.image

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Times a 1024x1024 decode the old way and the new, on the main thread (the two synchronous halves
 * around the browser's own decode, which Chromium runs off it) and end to end.
 *
 * Measured on headless Chromium (a production build; development is within a millisecond) with
 * a CPU canvas: main thread 70 ms before, 19 ms after; end to end 85 ms, 38 ms. On a SwiftShader
 * GPU canvas, as the consumer profile ran: main thread 88 ms, 19 ms; end to end 104 ms, 40 ms.
 * What remains is the browser's: `getImageData` (about 7.5 ms), the `Blob` (3.3 ms), and the byte
 * copies into and out of linear memory (5.6 ms). Only skipping the readback (step 2 of
 * awakekt/awake#1184) goes further. The floor asserted is a regression guard against the per-element
 * path (about 1x), kept low because a loaded runner measured as little as 2.6x.
 */
class BrowserBitmapDecodeBenchmark {
    @Test
    fun aTextureSizedDecode() = runTest(timeout = 2.minutes) {
        val png = PngFixtures.opaque1024.png
        val before = Timings()
        val after = Timings()
        repeat(WARM_UP + MEASURED) { run ->
            val keep = run >= WARM_UP
            before.measure(keep, { perElementBlob(png) }, { perElementDecode(it) }, { perElementRead(it) })
            after.measure(keep, { blobOf(png) }, { createUnconvertedImageBitmap(it).await<ImageBitmapHandle>() }, { image -> readBottomUpArgb(image).also { image.close() } })
        }
        println(
            "BrowserBitmapDecodeBenchmark 1024x1024: main thread per element ${before.mainThread / MEASURED}, bulk ${after.mainThread / MEASURED} " +
                "(${before.mainThread / after.mainThread}x); end to end per element ${before.total / MEASURED}, bulk ${after.total / MEASURED} " +
                "(${before.total / after.total}x)",
        )
        assertTrue(before.mainThread / after.mainThread >= MIN_MAIN_THREAD_SPEED_UP, "main thread ${before.mainThread} before, ${after.mainThread} after")
    }

    private class Timings {
        var mainThread = Duration.ZERO
        var total = Duration.ZERO

        // The browser decodes between the two synchronous halves, off the main thread in Chromium.
        suspend inline fun <B, I> measure(keep: Boolean, toBlob: () -> B, decode: suspend (B) -> I, read: (I) -> Bitmap) {
            val start = TimeSource.Monotonic.markNow()
            val blob = toBlob()
            val blobbed = start.elapsedNow()
            val image = decode(blob)
            val readStart = TimeSource.Monotonic.markNow()
            read(image)
            if (!keep) return
            mainThread += blobbed + readStart.elapsedNow()
            total += start.elapsedNow()
        }
    }

    private companion object {
        const val WARM_UP = 2
        const val MEASURED = 5
        const val MIN_MAIN_THREAD_SPEED_UP = 1.5
    }
}
