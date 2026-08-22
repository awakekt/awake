// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.testing

import io.github.ronjunevaldoz.awake.core.color.Color

/**
 * A CPU-side, tightly-packed, straight-alpha RGBA8 buffer -- the layout `Renderer.readPixels`,
 * `TextureAsset.data`, and `UiRasterizer` all already produce.
 *
 * This exists to give the pixel loops ONE [blend]. Before it, every rasterizer primitive
 * hand-wrote its own composite, and they had drifted apart: plain quads, gradients, and
 * triangles overwrote the destination and parked the source alpha in the alpha channel (so a
 * 10%-alpha fill rendered fully saturated), while glyphs and rounded quads blended -- using two
 * different formulas that only agree over an opaque destination. Because this buffer backs the
 * preview and parity snapshots, those were wrong *oracles*, not just wrong pixels.
 */
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

    /** Overwrites the pixel, ignoring whatever was there. Use for clears; [blend] for drawing. */
    fun set(x: Int, y: Int, color: Color) {
        val offset = offsetOf(x, y)
        pixels[offset] = channel(color.r)
        pixels[offset + 1] = channel(color.g)
        pixels[offset + 2] = channel(color.b)
        pixels[offset + 3] = channel(color.a)
    }

    fun blend(x: Int, y: Int, color: Color, coverage: Float = 1f) {
        blend(x, y, color.r * 255f, color.g * 255f, color.b * 255f, color.a * coverage)
    }

    /**
     * Straight-alpha source-over composite. [r]/[g]/[b] are 0..255, [srcA] is 0..1.
     *
     * The `/ outA` is what un-premultiplies the result back into this buffer's straight-alpha
     * layout. Dropping it (as one of the old inlined copies did) still looks right over an
     * opaque destination, where `outA` is 1, and only goes wrong once something translucent is
     * drawn over something else translucent.
     */
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
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                set(x, y, color)
                x += 1
            }
            y += 1
        }
    }

    private fun channel(unit: Float): Byte = channel255(unit * 255f)

    private fun channel255(value: Float): Byte = value.toInt().coerceIn(0, 255).toByte()
}
