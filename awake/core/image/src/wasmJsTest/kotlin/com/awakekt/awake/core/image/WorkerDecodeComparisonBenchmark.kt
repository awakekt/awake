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
import kotlin.test.assertContentEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Compares the worker decode ([WorkerDecode]) with the two shipped browser decodes, on the main thread,
 * for a 1024x1024 PNG: step 1's bulk readback ([createBitmap]) and step 2's GPU-bound decode
 * ([decodeBrowserImage], which reads nothing back).
 *
 * Measured on headless Chromium, CPU canvas, production build, four runs, main thread per decode:
 * bulk 30 to 48 ms, worker 16.5 to 30 ms, GPU-bound 12 to 16.5 ms. End to end: 64 to 132 ms, 64 to
 * 127 ms and 41 to 102 ms. The worker beat the bulk readback in every run, and never the GPU-bound
 * decode.
 *
 * What the worker leaves on the main thread is the `Blob` the GPU-bound decode also pays (this fixture
 * is an uncompressed 4 MB PNG), plus the copy of the packed pixels back into wasm and their load into
 * the [Bitmap]. So the worker takes 37 to 44% off a CPU reader's decode, but stays above the GPU-bound
 * decode. A texture decoded this way still uploads from the CPU, with a CPU mip chain, which
 * `ExternalImageUploadBrowserBenchmark` measured at about 200 ms for 1024x1024, against about 3 ms for
 * the GPU copy. For textures the GPU-direct path is the one to take; the worker only helps a caller
 * that reads the pixels. Nothing is asserted on the timing: this records a comparison, it guards nothing.
 *
 * Negative control, measured: the worker leaving the rows top-down fails the parity test at pixel 0
 * (0xff00000b, expected 0xfffffd06).
 */
class WorkerDecodeComparisonBenchmark {
    // First, that the comparison is fair: the worker's pixels are createBitmap's, bit for bit.
    @Test
    fun theWorkerDecodesWhatCreateBitmapDoes() = runTest(timeout = 2.minutes) {
        for (image in PngFixtures.all) {
            assertContentEquals(createBitmap(image.png).pixels, WorkerDecode.decode(image.png).pixels, image.name)
        }
    }

    @Test
    fun mainThreadTimeOfATextureSizedDecode() = runTest(timeout = 2.minutes) {
        val png = PngFixtures.opaque1024.png
        val bulk = Timings()
        val gpuBound = Timings()
        val worker = Timings()
        repeat(WARM_UP + MEASURED) { run ->
            val keep = run >= WARM_UP
            bulk.measure(keep, { blobOf(png) }, { createUnconvertedImageBitmap(it).await<ImageBitmapHandle>() }) { readBottomUpArgb(it).also { _ -> it.close() } }
            gpuBound.measure(keep, { blobOf(png) }, { createUnconvertedImageBitmap(it).await<ImageBitmapHandle>() }) { BrowserImage(it).close() }
            worker.measure(keep, { WorkerDecode.post(png) }, { it.await<JsAny>() }) { WorkerDecode.receive(it) }
        }
        println(
            "WorkerDecodeComparisonBenchmark 1024x1024 main thread: bulk ${bulk.mainThread / MEASURED}, " +
                "worker ${worker.mainThread / MEASURED}, GPU-bound ${gpuBound.mainThread / MEASURED}; " +
                "end to end: bulk ${bulk.total / MEASURED}, worker ${worker.total / MEASURED}, GPU-bound ${gpuBound.total / MEASURED}",
        )
    }

    private class Timings {
        var mainThread = Duration.ZERO
        var total = Duration.ZERO

        // The synchronous halves run on the main thread; between them it waits, free, for the decode.
        suspend inline fun <B, I> measure(keep: Boolean, start: () -> B, wait: suspend (B) -> I, finish: (I) -> Unit) {
            val begin = TimeSource.Monotonic.markNow()
            val started = start()
            val first = begin.elapsedNow()
            val waited = wait(started)
            val finishing = TimeSource.Monotonic.markNow()
            finish(waited)
            if (!keep) return
            mainThread += first + finishing.elapsedNow()
            total += begin.elapsedNow()
        }
    }

    private companion object {
        const val WARM_UP = 2
        const val MEASURED = 5
    }
}
