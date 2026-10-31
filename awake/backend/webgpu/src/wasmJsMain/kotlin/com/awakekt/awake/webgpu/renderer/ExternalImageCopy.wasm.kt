/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.image.BrowserImage
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.Queue
import io.ygdrasil.webgpu.WGPUQueue
import io.ygdrasil.webgpu.WGPUTexture
import kotlin.js.ExperimentalWasmJsInterop
import io.ygdrasil.webgpu.Texture as WgpuTexture

internal actual fun GPUQueue.canCopyExternalImage(image: Any?): Boolean =
    image is BrowserImage && !image.isClosed && hasCopyExternalImage((this as Queue).handler)

// The browser's own call rather than wgpu4k's, which is deprecated and takes a DrawableHolder.
internal actual fun GPUQueue.copyExternalImage(image: Any, texture: GPUTexture, width: Int, height: Int) {
    copyImageBitmap((this as Queue).handler, (image as BrowserImage).imageBitmap, (texture as WgpuTexture).handler, width, height)
}

@Suppress("UnusedParameter") // Read by the JS body.
private fun hasCopyExternalImage(queue: WGPUQueue): Boolean = js("typeof queue.copyExternalImageToTexture === 'function'")

// flipY puts the image's bottom row first, as TextureAsset.data holds it. premultipliedAlpha: false
// keeps the bitmap's straight alpha, which createImageBitmap was told not to premultiply.
@Suppress("UnusedParameter") // Read by the JS body.
private fun copyImageBitmap(queue: WGPUQueue, bitmap: JsAny, texture: WGPUTexture, width: Int, height: Int): Unit = js(
    """queue.copyExternalImageToTexture(
        { source: bitmap, flipY: true },
        { texture: texture, premultipliedAlpha: false },
        [width, height],
    )""",
)
