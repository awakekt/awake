/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.image

import kotlinx.coroutines.await

/**
 * Platform actual decoding encoded image [bytes] in browser via `createImageBitmap` and Canvas 2D.
 *
 * The browser decodes the image (any format it supports, no sniffing needed), one shared 2D
 * context reads the pixels back, and they reach Kotlin through wasm linear memory in a single JS
 * call: no JS call is made per pixel or per byte (awakekt/awake#1184).
 *
 * @param bytes The raw encoded image byte array.
 * @return A decoded [Bitmap] instance with Y-axis flipped for texture orientation.
 */
actual suspend fun createBitmap(bytes: ByteArray): Bitmap {
    val image = createUnconvertedImageBitmap(blobOf(bytes)).await<ImageBitmapHandle>()
    try {
        return readBottomUpArgb(image)
    } finally {
        image.close()
    }
}
