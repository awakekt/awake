/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.image

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * The alternative #1184's checklist names, to compare with the GPU-direct upload: the whole decode in a
 * Web Worker. The main thread wraps the bytes in a `Blob` (as [decodeBrowserImage] does) and posts it.
 * The worker decodes with `createImageBitmap` (no colour conversion, straight alpha, as the main-thread
 * decode), reads the pixels back from its own `OffscreenCanvas`, flips the rows and packs ARGB. The main
 * thread then copies the packed ints into wasm memory in one call and loads them into the [Bitmap].
 *
 * Test-only: [WorkerDecodeComparisonBenchmark] times it, nothing ships it.
 */
internal object WorkerDecode {
    private var worker: JsAny? = null

    /** The posting half: what the main thread does before the worker takes over. */
    fun post(bytes: ByteArray): Promise<JsAny> = postToWorker(worker ?: startWorker().also { worker = it }, blobOf(bytes))

    /** The receiving half: the worker's packed pixels into a [Bitmap], one copy and a load per pixel. */
    fun receive(reply: JsAny): Bitmap {
        val width = replyWidth(reply)
        val height = replyHeight(reply)
        val pixels = IntArray(width * height)
        if (pixels.isNotEmpty()) {
            withScopedMemoryAllocator { allocator ->
                val base = allocator.allocate(pixels.size * Int.SIZE_BYTES)
                copyReplyIntoLinearMemory(reply, wasmMemory, base.address.toInt())
                for (i in pixels.indices) pixels[i] = (base + i * Int.SIZE_BYTES).loadInt()
            }
        }
        return DefaultBitmap(width, height, Int.SIZE_BYTES, pixels)
    }

    suspend fun decode(bytes: ByteArray): Bitmap = receive(post(bytes).await())
}

// The worker's own script, from a Blob URL: decode, read back, flip to bottom row first, pack 0xAARRGGBB.
private fun startWorker(): JsAny = js(
    """(() => {
        const source = `
            const canvas = new OffscreenCanvas(1, 1);
            const context = canvas.getContext('2d', { willReadFrequently: true });
            onmessage = async (event) => {
                const image = await createImageBitmap(event.data, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' });
                const width = image.width, height = image.height;
                canvas.width = width; canvas.height = height;
                context.clearRect(0, 0, width, height);
                context.drawImage(image, 0, 0);
                image.close();
                const rgba = context.getImageData(0, 0, width, height).data;
                const argb = new Int32Array(width * height);
                for (let y = 0; y < height; y++) {
                    const from = (height - 1 - y) * width * 4, to = y * width;
                    for (let x = 0; x < width; x++) {
                        const at = from + x * 4;
                        argb[to + x] = (rgba[at + 3] << 24) | (rgba[at] << 16) | (rgba[at + 1] << 8) | rgba[at + 2];
                    }
                }
                postMessage({ width, height, argb }, [argb.buffer]);
            };`;
        return new Worker(URL.createObjectURL(new Blob([source], { type: 'text/javascript' })));
    })()""",
)

@Suppress("UnusedParameter") // Read by the JS body.
private fun postToWorker(worker: JsAny, blob: JsAny): Promise<JsAny> = js(
    "new Promise((resolve) => { worker.onmessage = (event) => resolve(event.data); worker.postMessage(blob); })",
)

@Suppress("UnusedParameter") // Read by the JS body.
private fun replyWidth(reply: JsAny): Int = js("reply.width")

@Suppress("UnusedParameter") // Read by the JS body.
private fun replyHeight(reply: JsAny): Int = js("reply.height")

@Suppress("UnusedParameter") // Read by the JS body.
private fun copyReplyIntoLinearMemory(reply: JsAny, memory: JsAny, byteOffset: Int): Unit =
    js("new Int32Array(memory.buffer, byteOffset, reply.argb.length).set(reply.argb)")
