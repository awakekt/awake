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
 * Times a 1024x1024 decode three ways, on the main thread (the two synchronous halves around the
 * browser's own decode, which Chromium runs off it) and end to end: the old per-element decode, step 1's
 * bulk readback, and step 2's GPU-bound decode (`decodeBrowserImage`), which reads nothing back because
 * the WebGPU backend copies the `ImageBitmap` straight into a texture (awakekt/awake#1184).
 *
 * Step 1, measured on headless Chromium (a production build) with a CPU canvas: main thread 70 ms
 * before, 19 ms after; end to end 85 ms, 38 ms. On a SwiftShader GPU canvas, as the consumer profile
 * ran: main thread 88 ms, 19 ms; end to end 104 ms, 40 ms.
 *
 * Step 2, measured on a slower container (CPU canvas, production build, three runs): main
 * thread per element 129 to 154 ms, bulk 37 to 50 ms, GPU-bound 12.8 to 16.2 ms, which is 8 to 12x
 * the per-element decode and 2.3 to 3.9x step 1. What the GPU-bound decode still costs is the copy of
 * the file into a `Blob`: this fixture is an uncompressed 4 MB PNG, so a real file costs less.
 *
 * The floors are regression guards, kept at 40 to 60% of the lowest measured ratios because a loaded runner
 * measured step 1 as low as 2.6x: step 1 against the per-element path, and the GPU-bound decode against
 * both, so a readback slipping back into it fails.
 */
class BrowserBitmapDecodeBenchmark {
    @Test
    fun aTextureSizedDecode() = runTest(timeout = 2.minutes) {
        val png = PngFixtures.opaque1024.png
        val before = Timings()
        val after = Timings()
        val gpuBound = Timings()
        repeat(WARM_UP + MEASURED) { run ->
            val keep = run >= WARM_UP
            before.measure(keep, { perElementBlob(png) }, { perElementDecode(it) }, { perElementRead(it) })
            after.measure(keep, { blobOf(png) }, { createUnconvertedImageBitmap(it).await<ImageBitmapHandle>() }, { image -> readBottomUpArgb(image).also { image.close() } })
            // Step 2: what decodeBrowserImage does for a GPU upload, which reads nothing back.
            gpuBound.measure(keep, { blobOf(png) }, { createUnconvertedImageBitmap(it).await<ImageBitmapHandle>() }, { image -> BrowserImage(image).close() })
        }
        println(
            "BrowserBitmapDecodeBenchmark 1024x1024: main thread per element ${before.mainThread / MEASURED}, bulk ${after.mainThread / MEASURED} " +
                "(${before.mainThread / after.mainThread}x), GPU-bound ${gpuBound.mainThread / MEASURED} (${before.mainThread / gpuBound.mainThread}x); " +
                "end to end per element ${before.total / MEASURED}, bulk ${after.total / MEASURED} (${before.total / after.total}x), " +
                "GPU-bound ${gpuBound.total / MEASURED} (${before.total / gpuBound.total}x)",
        )
        assertTrue(before.mainThread / after.mainThread >= MIN_MAIN_THREAD_SPEED_UP, "main thread ${before.mainThread} before, ${after.mainThread} after")
        assertTrue(
            before.mainThread / gpuBound.mainThread >= MIN_GPU_BOUND_SPEED_UP,
            "main thread ${before.mainThread} per element, ${gpuBound.mainThread} GPU-bound",
        )
        assertTrue(
            after.mainThread / gpuBound.mainThread >= MIN_GPU_BOUND_OVER_BULK,
            "main thread ${after.mainThread} bulk, ${gpuBound.mainThread} GPU-bound",
        )
    }

    private class Timings {
        var mainThread = Duration.ZERO
        var total = Duration.ZERO

        // The browser decodes between the two synchronous halves, off the main thread in Chromium.
        suspend inline fun <B, I> measure(keep: Boolean, toBlob: () -> B, decode: suspend (B) -> I, read: (I) -> Unit) {
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
        const val MIN_GPU_BOUND_SPEED_UP = 4.0
        const val MIN_GPU_BOUND_OVER_BULK = 1.3
    }
}
