/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.image

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop

/**
 * An image the browser decoded and still holds, with none of its pixels in wasm memory: what a GPU
 * backend copies straight into a texture (WebGPU's `copyExternalImageToTexture`), so the main thread
 * never reads it back (step 2 of awakekt/awake#1184).
 *
 * It is decoded exactly as [createBitmap] decodes, with no colour-space conversion and no alpha
 * premultiplication, and top row first, as the browser stores every image. A GPU upload therefore
 * flips it, and [toBitmap] returns the bottom-row-first pixels [createBitmap] would have returned,
 * bit for bit.
 *
 * Use it when the pixels only go to the GPU. A caller that reads pixels on the CPU (collision masks,
 * an atlas packer, a mip chain built on the CPU) uses [createBitmap], or [toBitmap] on this.
 *
 * The browser keeps the decoded image until [close]; close it once it is uploaded.
 */
class BrowserImage internal constructor(private var handle: ImageBitmapHandle?) : AutoCloseable {
    /** Width in pixels. */
    val width: Int = checkNotNull(handle).width

    /** Height in pixels. */
    val height: Int = checkNotNull(handle).height

    /** Whether [close] has released the browser's image. */
    val isClosed: Boolean get() = handle == null

    /**
     * The browser `ImageBitmap`, top row first, straight alpha, unconverted colour: the `source` of a
     * `GPUQueue.copyExternalImageToTexture`, which flips it with `flipY: true`.
     *
     * @throws IllegalStateException once closed.
     */
    val imageBitmap: JsAny get() = openHandle()

    /**
     * Reads the pixels back to the CPU: what [createBitmap] returns for the same bytes, bottom row first.
     * This is the readback a GPU upload avoids; it costs what step 1's decode does.
     *
     * @throws IllegalStateException once closed.
     */
    fun toBitmap(): Bitmap = readBottomUpArgb(openHandle())

    /** Releases the browser's decoded image. Idempotent. */
    override fun close() {
        handle?.close()
        handle = null
    }

    private fun openHandle(): ImageBitmapHandle = checkNotNull(handle) { "This BrowserImage is closed." }
}

/**
 * Decodes encoded image [bytes] (any format the browser reads) into a [BrowserImage] for a GPU upload.
 * The bytes cross into the browser in one call; the decode itself runs off the main thread in Chromium.
 *
 * @throws Throwable the browser's decode error when [bytes] are not an image it can read.
 */
suspend fun decodeBrowserImage(bytes: ByteArray): BrowserImage =
    BrowserImage(createUnconvertedImageBitmap(blobOf(bytes)).await<ImageBitmapHandle>())
