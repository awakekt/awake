/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * An image file the browser encodes, `image/png` or `image/jpeg`, of a [width]x[height] pattern in which
 * every channel varies along both axes, so a flip, a mirror or a channel swap changes the pixels. With
 * [translucent], alpha sweeps 0..255 along x. A JPEG's colour is sRGB: the browser writes no other.
 */
internal suspend fun encodedImage(type: String, width: Int, height: Int, translucent: Boolean): ByteArray {
    val buffer = encodePattern(type, width, height, translucent).await<ArrayBuffer>()
    return Int8Array(buffer).toByteArray()
}

@Suppress("UnusedParameter") // Read by the JS body.
private fun encodePattern(type: String, width: Int, height: Int, translucent: Boolean): Promise<ArrayBuffer> = js(
    """(() => {
        const canvas = new OffscreenCanvas(width, height);
        const context = canvas.getContext('2d');
        const image = context.createImageData(width, height);
        const data = image.data;
        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                const at = (y * width + x) * 4;
                data[at] = (x * 7 + y) & 255;
                data[at + 1] = (y * 3 + (x >> 2)) & 255;
                data[at + 2] = (((x ^ y) * 5) + 11) & 255;
                data[at + 3] = translucent ? Math.min(255, Math.floor(x * 256 / width)) : 255;
            }
        }
        context.putImageData(image, 0, 0);
        return canvas.convertToBlob({ type: type, quality: 0.9 }).then((blob) => blob.arrayBuffer());
    })()""",
)
