/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.capture

import com.awakekt.awake.core.color.Color

/** A CPU-side, tightly-packed, straight-alpha RGBA8 buffer used by render diagnostics. */
class PixelMap(
    val width: Int,
    val height: Int,
    val pixels: ByteArray = ByteArray(width * height * 4),
) {
    init {
        require(width > 0 && height > 0) { "width and height must be positive, got ${width}x$height" }
        require(pixels.size == width * height * 4) {
            "Pixel buffer size mismatch: actual=${pixels.size} bytes, expected=${width * height * 4} bytes"
        }
    }

    fun offsetOf(x: Int, y: Int): Int = (y * width + x) * 4

    fun contains(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun sample(x: Int, y: Int): RgbaSample {
        val offset = offsetOf(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
        return RgbaSample(
            r = pixels[offset].toInt() and 0xFF,
            g = pixels[offset + 1].toInt() and 0xFF,
            b = pixels[offset + 2].toInt() and 0xFF,
            a = pixels[offset + 3].toInt() and 0xFF,
        )
    }

    fun set(x: Int, y: Int, color: Color) {
        val offset = offsetOf(x, y)
        pixels[offset] = channel(color.r)
        pixels[offset + 1] = channel(color.g)
        pixels[offset + 2] = channel(color.b)
        pixels[offset + 3] = channel(color.a)
    }

    fun blend(x: Int, y: Int, color: Color, coverage: Float = 1f) =
        blend(x, y, color.r * 255f, color.g * 255f, color.b * 255f, color.a * coverage)

    fun blend(x: Int, y: Int, r: Float, g: Float, b: Float, srcA: Float) {
        if (srcA <= 0f) return
        val offset = offsetOf(x, y)
        if (srcA >= 1f) {
            pixels[offset] = channel255(r)
            pixels[offset + 1] = channel255(g)
            pixels[offset + 2] = channel255(b)
            pixels[offset + 3] = 255.toByte()
            return
        }
        val dstA = (pixels[offset + 3].toInt() and 0xFF) / 255f
        val outA = srcA + dstA * (1f - srcA)
        if (outA <= 0f) return
        val inv = dstA * (1f - srcA)
        val dstR = pixels[offset].toInt() and 0xFF
        val dstG = pixels[offset + 1].toInt() and 0xFF
        val dstB = pixels[offset + 2].toInt() and 0xFF
        pixels[offset] = channel255((r * srcA + dstR * inv) / outA)
        pixels[offset + 1] = channel255((g * srcA + dstG * inv) / outA)
        pixels[offset + 2] = channel255((b * srcA + dstB * inv) / outA)
        pixels[offset + 3] = channel255(outA * 255f)
    }

    fun fill(color: Color) {
        val red = channel(color.r)
        val green = channel(color.g)
        val blue = channel(color.b)
        val alpha = channel(color.a)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val offset = (y * width + x) * 4
                pixels[offset] = red
                pixels[offset + 1] = green
                pixels[offset + 2] = blue
                pixels[offset + 3] = alpha
                x += 1
            }
            y += 1
        }
    }

    /** Returns this image composited over an opaque background without changing this image. */
    fun compositeOver(background: Color): PixelMap {
        val result = PixelMap(width, height)
        result.fill(background)
        val backgroundR = channel(background.r).toInt() and 0xFF
        val backgroundG = channel(background.g).toInt() and 0xFF
        val backgroundB = channel(background.b).toInt() and 0xFF
        var offset = 0
        while (offset < pixels.size) {
            val sourceA = pixels[offset + 3].toInt() and 0xFF
            val inverseA = 255 - sourceA
            result.pixels[offset] = (((pixels[offset].toInt() and 0xFF) * sourceA + backgroundR * inverseA) / 255)
                .coerceIn(0, 255).toByte()
            result.pixels[offset + 1] = (((pixels[offset + 1].toInt() and 0xFF) * sourceA + backgroundG * inverseA) / 255)
                .coerceIn(0, 255).toByte()
            result.pixels[offset + 2] = (((pixels[offset + 2].toInt() and 0xFF) * sourceA + backgroundB * inverseA) / 255)
                .coerceIn(0, 255).toByte()
            result.pixels[offset + 3] = 255.toByte()
            offset += 4
        }
        return result
    }

    private fun channel(unit: Float): Byte = channel255(unit * 255f)

    private fun channel255(value: Float): Byte = value.toInt().coerceIn(0, 255).toByte()
}

data class RgbaSample(val r: Int, val g: Int, val b: Int, val a: Int)
