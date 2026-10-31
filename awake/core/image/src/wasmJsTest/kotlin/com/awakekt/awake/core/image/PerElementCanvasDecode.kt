/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.image

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8ClampedArray
import org.khronos.webgl.get
import org.khronos.webgl.toInt8Array
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.ImageBitmap
import org.w3c.files.Blob
import kotlin.js.JsAny
import kotlin.js.toJsArray

/**
 * The wasm `createBitmap` as it was before awakekt/awake#1184, kept verbatim as the reference the
 * new decode must match bit for bit, and as the "before" side of its timing: a `<canvas>` per
 * decode, and a JS call per byte read out of `getImageData`.
 */
internal suspend fun perElementCanvasDecode(bytes: ByteArray): Bitmap = perElementRead(perElementDecode(perElementBlob(bytes)))

// Split at the await, so the main-thread halves can be timed apart from the browser's own decode.
internal fun perElementBlob(bytes: ByteArray): Blob = Blob(arrayOf<JsAny?>(bytes.toInt8Array()).toJsArray())

internal suspend fun perElementDecode(blob: Blob): ImageBitmap = window.createImageBitmap(blob).await()

internal fun perElementRead(imageBitmap: ImageBitmap): Bitmap {
    val width = imageBitmap.width
    val height = imageBitmap.height

    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = width
    canvas.height = height
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    ctx.drawImage(imageBitmap, 0.0, 0.0)
    val data: Uint8ClampedArray = ctx.getImageData(0.0, 0.0, width.toDouble(), height.toDouble()).data

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val flippedY = height - 1 - y
        for (x in 0 until width) {
            val offset = (flippedY * width + x) * 4
            val r = data[offset].toInt() and 0xFF
            val g = data[offset + 1].toInt() and 0xFF
            val b = data[offset + 2].toInt() and 0xFF
            val a = data[offset + 3].toInt() and 0xFF
            pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    return DefaultBitmap(width, height, 4, pixels)
}

/**
 * Counts every canvas made from here on, `<canvas>` or `OffscreenCanvas`, attached or not, by
 * wrapping their constructors once; [canvasesCreated] reads the count.
 */
internal fun countCanvasCreation(): Unit = js(
    """{
        if (globalThis.__awakeCanvasCount !== undefined) return;
        globalThis.__awakeCanvasCount = 0;
        const createElement = Document.prototype.createElement;
        Document.prototype.createElement = function (tag, options) {
            if (String(tag).toLowerCase() === 'canvas') globalThis.__awakeCanvasCount++;
            return createElement.call(this, tag, options);
        };
        if (typeof OffscreenCanvas !== 'undefined') {
            const Offscreen = OffscreenCanvas;
            globalThis.OffscreenCanvas = class extends Offscreen {
                constructor(w, h) { super(w, h); globalThis.__awakeCanvasCount++; }
            };
        }
    }""",
)

internal fun canvasesCreated(): Int = js("globalThis.__awakeCanvasCount")
