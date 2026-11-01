/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

import com.awakekt.awake.core.image.DecodedImage
import com.awakekt.awake.core.image.toRgba8Bytes

/**
 * A decoded 2D image a backend may copy straight to a GPU texture, without the CPU pixels a
 * [TextureAsset] carries: in a browser, an `ImageBitmap` that WebGPU's `copyExternalImageToTexture`
 * uploads, so its pixels never cross into wasm (awakekt/awake#1184).
 *
 * `GpuDevice.createImageMaterial` uploads one. It holds no pixels of its own. The decoded image
 * belongs to whoever made this source, and they release it after the upload.
 */
interface GpuImageSource {
    /** Width in pixels. */
    val width: Int

    /** Height in pixels. */
    val height: Int

    /**
     * The platform image a backend copies on the GPU, or null when there is none and only
     * [readRgba8] can supply the pixels. On wasm it is `awake:core:image`'s `BrowserImage`: a browser
     * `ImageBitmap`, top row first, straight alpha, no colour-space conversion. A backend that does not
     * recognise the object ignores it and reads [readRgba8] instead.
     */
    val externalImage: Any?

    /**
     * The pixels as [TextureAsset.data] holds them: tightly packed straight-alpha RGBA8, bottom row
     * first. This is the CPU path, and a slow one on wasm, where it is a canvas readback. A backend
     * calls it only when it cannot copy [externalImage].
     */
    fun readRgba8(): ByteArray

    /**
     * The image already in a GPU block format, with its mips, such as a cooked KTX2 texture (#1211), or
     * null for one that only has pixels. `createImageMaterial` uploads it as it is on a device that
     * takes its format, and [readRgba8] otherwise. Null by default.
     */
    val compressed: CompressedTextureImage? get() = null
}

/**
 * This decoded image as a [GpuImageSource]: in a browser its `ImageBitmap` is the
 * [GpuImageSource.externalImage] a WebGPU backend copies, and anywhere else [GpuImageSource.readRgba8]
 * supplies the CPU pixels. The image stays the caller's, open for as long as a material may be made
 * from it.
 */
fun DecodedImage.asGpuImageSource(): GpuImageSource = DecodedImageSource(this)

/** This single-layer 2D texture as a [GpuImageSource] with no platform image: it always takes the CPU path. */
fun TextureAsset.asGpuImageSource(): GpuImageSource {
    require(layerCount == 1 && !isCubemap) { "Only a single 2D image is a GpuImageSource; this has $layerCount layer(s)." }
    return AssetImageSource(this)
}

private class DecodedImageSource(private val image: DecodedImage) : GpuImageSource {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override val externalImage: Any? get() = image.platformImage

    override fun readRgba8(): ByteArray = image.toBitmap().toRgba8Bytes()
}

private class AssetImageSource(private val asset: TextureAsset) : GpuImageSource {
    override val width: Int get() = asset.width
    override val height: Int get() = asset.height
    override val externalImage: Any? get() = null

    override fun readRgba8(): ByteArray = asset.data
}
