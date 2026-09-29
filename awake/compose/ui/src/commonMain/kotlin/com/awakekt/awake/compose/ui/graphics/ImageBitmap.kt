/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.image.createBitmap

/**
 * Decoded pixels a UI can draw: [width]x[height], straight (not premultiplied) RGBA8, top row first.
 *
 * Renderer-neutral: drawing one emits a texture slot the renderer-aware host fills with a GPU
 * texture the first time it sees this instance, and frees once the image stops being drawn. The
 * instance is the identity, so keep and reuse it -- decoding again makes a new upload.
 */
class ImageBitmap(
    val width: Int,
    val height: Int,
    /** Tightly packed RGBA8, [width] * [height] * 4 bytes, rows top to bottom. */
    val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "An image needs a positive size; was ${width}x$height." }
        require(pixels.size == width * height * BYTES_PER_PIXEL) {
            "A ${width}x$height image needs ${width * height * BYTES_PER_PIXEL} RGBA8 bytes, got ${pixels.size}."
        }
    }

    /** Straight RGBA of the pixel at [x], [y], each channel 0..255. */
    fun pixel(x: Int, y: Int): Int {
        val offset = (y * width + x) * BYTES_PER_PIXEL
        return (channel(offset) shl 24) or (channel(offset + 1) shl 16) or
            (channel(offset + 2) shl 8) or channel(offset + 3)
    }

    private fun channel(offset: Int) = pixels[offset].toInt() and 0xFF

    override fun toString(): String = "ImageBitmap(${width}x$height)"

    companion object {
        const val BYTES_PER_PIXEL: Int = 4
    }
}

/**
 * Decodes PNG or JPEG [bytes] with the platform's decoder.
 *
 * Suspends because the browser decodes asynchronously; decode outside composition and hand the
 * result to the UI on a later frame.
 */
suspend fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    val bitmap = createBitmap(bytes)
    val width = bitmap.width
    val height = bitmap.height
    val pixels = ByteArray(width * height * ImageBitmap.BYTES_PER_PIXEL)
    // createBitmap returns rows bottom-up (the texture-origin convention of its first consumer)
    // as 0xAARRGGBB ints; ImageBitmap is top-down RGBA8.
    for (y in 0 until height) {
        val sourceRow = (height - 1 - y) * width
        for (x in 0 until width) {
            val argb = bitmap.pixels[sourceRow + x]
            val offset = (y * width + x) * ImageBitmap.BYTES_PER_PIXEL
            pixels[offset] = (argb shr 16).toByte()
            pixels[offset + 1] = (argb shr 8).toByte()
            pixels[offset + 2] = argb.toByte()
            pixels[offset + 3] = (argb ushr 24).toByte()
        }
    }
    return ImageBitmap(width, height, pixels)
}

/**
 * Draws [image] stretched into the rectangle [x], [y], [width], [height] of this node, which
 * defaults to the whole node. Fitting or cropping is the caller's choice; see `Image`.
 */
fun DrawScope.drawImage(
    image: ImageBitmap,
    x: Float = 0f,
    y: Float = 0f,
    width: Float = this.width.toFloat(),
    height: Float = this.height.toFloat(),
) {
    drawTexture(image, x, y, width, height)
}
