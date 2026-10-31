/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.core.image.BrowserImage
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.texture.GpuImageSource

/**
 * This image as a [GpuImageSource], for `GpuDevice.createImageMaterial`: the WebGPU renderer copies
 * it on the GPU, and any other device reads it back with [BrowserImage.toBitmap]. The image stays
 * this caller's, to close once the material is made.
 */
fun BrowserImage.asGpuImageSource(): GpuImageSource = BrowserImageSource(this)

private class BrowserImageSource(private val image: BrowserImage) : GpuImageSource {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override val externalImage: Any get() = image

    override fun readRgba8(): ByteArray = image.toBitmap().toRgba8Bytes()
}
