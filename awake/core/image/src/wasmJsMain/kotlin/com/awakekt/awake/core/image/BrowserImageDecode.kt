/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.image

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

// A Kotlin/Wasm array lives in the GC heap, where JS cannot see it, so reading a typed array element
// by element costs a JS call per element: four per pixel in the old decode. Here the bytes cross
// through wasm linear memory instead, in one JS call each way, and every per-pixel step is a plain
// wasm load or store.

/** A `Blob` holding [bytes], copied out of linear memory by JS in one call. */
internal fun blobOf(bytes: ByteArray): JsAny = withScopedMemoryAllocator { allocator ->
    val base = allocator.allocate(maxOf(bytes.size, 1))
    storeBytes(base, bytes)
    // The Blob copies the view as it is built, before the scoped allocation is released.
    blobOfLinearMemory(wasmMemory, base.address.toInt(), bytes.size)
}

/**
 * The pixels of [image] as `0xAARRGGBB` ints, bottom row first: the layout `BufferedImage.getRGB`
 * gives the desktop decode, and every `createBitmap` actual returns.
 */
internal fun readBottomUpArgb(image: ImageBitmapHandle): Bitmap {
    val width = image.width
    val height = image.height
    val pixels = IntArray(width * height)
    if (pixels.isNotEmpty()) {
        withScopedMemoryAllocator { allocator ->
            val base = allocator.allocate(pixels.size * BYTES_PER_PIXEL)
            // Linear memory is read after the allocation, which may have grown it.
            drawIntoLinearMemory(readbackContext(), image, wasmMemory, base.address.toInt())
            packBottomUpArgb(base, width, height, pixels)
        }
    }
    return DefaultBitmap(width, height, BYTES_PER_PIXEL, pixels)
}

/** Top-down RGBA8 at [base], as `getImageData` lays it out, into bottom-up ARGB [pixels]. */
private fun packBottomUpArgb(base: Pointer, width: Int, height: Int, pixels: IntArray) {
    for (y in 0 until height) {
        val sourceRow = base + (height - 1 - y) * width * BYTES_PER_PIXEL
        val targetRow = y * width
        for (x in 0 until width) {
            // Little-endian: R is the low byte and A the high one, so G and A already sit where ARGB wants them.
            val rgba = (sourceRow + x * BYTES_PER_PIXEL).loadInt()
            pixels[targetRow + x] = (rgba and ALPHA_GREEN) or ((rgba and BYTE) shl RED_SHIFT) or ((rgba ushr BLUE_SHIFT) and BYTE)
        }
    }
}

/** A word at a time, little-endian as linear memory is, then the bytes left over. */
private fun storeBytes(base: Pointer, bytes: ByteArray) {
    val words = bytes.size / Int.SIZE_BYTES
    for (word in 0 until words) {
        val at = word * Int.SIZE_BYTES
        val packed = (bytes[at].toInt() and BYTE) or
            ((bytes[at + 1].toInt() and BYTE) shl BYTE_1) or
            ((bytes[at + 2].toInt() and BYTE) shl BYTE_2) or
            (bytes[at + 3].toInt() shl BYTE_3)
        (base + at).storeInt(packed)
    }
    for (at in words * Int.SIZE_BYTES until bytes.size) (base + at).storeByte(bytes[at])
}

private var sharedReadbackContext: JsAny? = null

/** One 2D context for every decode, never a new `<canvas>` per texture. */
private fun readbackContext(): JsAny = sharedReadbackContext ?: createReadbackContext().also { sharedReadbackContext = it }

/**
 * Decodes [blob] without the browser's colour-space conversion or alpha premultiplication, so
 * the pixels are the file's own values, as the desktop decode reads them.
 */
@Suppress("UnusedParameter") // Read by the JS body.
internal fun createUnconvertedImageBitmap(blob: JsAny): Promise<ImageBitmapHandle> =
    js("createImageBitmap(blob, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' })")

@Suppress("UnusedParameter") // Read by the JS body.
private fun blobOfLinearMemory(memory: JsAny, byteOffset: Int, byteSize: Int): JsAny =
    js("new Blob([new Uint8Array(memory.buffer, byteOffset, byteSize)])")

// willReadFrequently keeps the canvas on the CPU, where getImageData needs no GPU readback.
private fun createReadbackContext(): JsAny = js(
    """(typeof OffscreenCanvas !== 'undefined' ? new OffscreenCanvas(1, 1) : document.createElement('canvas'))
        .getContext('2d', { willReadFrequently: true })""",
)

// Resizing clears the canvas; at the same size the last image is cleared instead, as drawImage blends.
@Suppress("UnusedParameter") // Read by the JS body.
private fun drawIntoLinearMemory(context: JsAny, image: JsAny, memory: JsAny, byteOffset: Int): Unit = js(
    """{
        const canvas = context.canvas;
        const width = image.width;
        const height = image.height;
        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
        } else {
            context.clearRect(0, 0, width, height);
        }
        context.drawImage(image, 0, 0);
        const rgba = context.getImageData(0, 0, width, height).data;
        new Uint8Array(memory.buffer, byteOffset, rgba.length).set(rgba);
    }""",
)

private const val BYTES_PER_PIXEL = 4
private const val BYTE = 0xFF
private const val ALPHA_GREEN = 0xFF00FF00.toInt()
private const val RED_SHIFT = 16
private const val BLUE_SHIFT = 16
private const val BYTE_1 = 8
private const val BYTE_2 = 16
private const val BYTE_3 = 24
