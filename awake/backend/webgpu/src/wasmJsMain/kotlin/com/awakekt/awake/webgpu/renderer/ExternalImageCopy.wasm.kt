/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.image.BrowserImage
import com.awakekt.awake.core.logging.Logger
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
// A browser can refuse the copy (a source it can't read, a driver that won't), and the call throws. That
// mustn't escape into the frame, which would stop the frame loop: the caller uploads the CPU pixels.
internal actual fun GPUQueue.copyExternalImage(image: Any, texture: GPUTexture, width: Int, height: Int): Boolean =
    try {
        copyImageBitmap((this as Queue).handler, (image as BrowserImage).imageBitmap, (texture as WgpuTexture).handler, width, height)
        true
    } catch (refused: Throwable) {
        if (!refusalLogged) {
            refusalLogged = true
            copyLog.warn { "copyExternalImageToTexture refused ($width x $height): ${refused.message}; uploading CPU pixels instead" }
        }
        false
    }

private val copyLog = Logger("WebGpuExternalImage")

// Once a page: a browser that refuses one image usually refuses them all.
private var refusalLogged = false

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
