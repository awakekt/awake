/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

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
}
