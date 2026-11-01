/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.TextureRole
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** An 8-bit RGBA image, four bytes a pixel, its bottom row first as every Awake texture is. */
internal class RgbaImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(pixels.size == width * height * CHANNELS) { "a ${width}x$height image is ${width * height * CHANNELS} bytes; was ${pixels.size}" }
    }

    fun channel(x: Int, y: Int, c: Int): Int = pixels[(y * width + x) * CHANNELS + c].toInt() and BYTE
}

internal const val CHANNELS = 4
private const val BYTE = 0xFF
private const val MAX = 255f

/** Decoding a source image, and filtering its mips the way its [TextureRole] needs. */
internal object MipChain {

    /** Decodes [bytes], a PNG or JPEG, to RGBA, bottom row first. */
    fun decode(bytes: ByteArray): RgbaImage {
        val image = requireNotNull(ImageIO.read(ByteArrayInputStream(bytes))) { "not an image Java can decode" }
        val pixels = ByteArray(image.width * image.height * CHANNELS)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val at = ((image.height - 1 - y) * image.width + x) * CHANNELS
                pixels[at] = (argb shr RED_SHIFT).toByte()
                pixels[at + 1] = (argb shr GREEN_SHIFT).toByte()
                pixels[at + 2] = argb.toByte()
                pixels[at + 3] = (argb ushr ALPHA_SHIFT).toByte()
            }
        }
        return RgbaImage(image.width, image.height, pixels)
    }

    /** [base] and, with [mipmaps], each smaller level down to 1 x 1. */
    fun levels(base: RgbaImage, role: TextureRole, mipmaps: Boolean): List<RgbaImage> {
        if (!mipmaps) return listOf(base)
        return generateSequence(base) { if (it.width == 1 && it.height == 1) null else halve(it, role) }.toList()
    }

    /**
     * [image] at half its size, each pixel the mean of the 2 x 2 it covers (a side of 1 stays 1):
     * averaged in linear light for a [TextureRole.Color] image, and renormalised for a normal map.
     */
    fun halve(image: RgbaImage, role: TextureRole): RgbaImage {
        val width = maxOf(1, image.width / 2)
        val height = maxOf(1, image.height / 2)
        val pixels = ByteArray(width * height * CHANNELS)
        val sum = FloatArray(CHANNELS)
        for (y in 0 until height) {
            for (x in 0 until width) {
                mean(image, x * 2, y * 2, role, sum)
                val at = (y * width + x) * CHANNELS
                for (c in 0 until CHANNELS) pixels[at + c] = encoded(sum[c], c, role).toByte()
            }
        }
        return RgbaImage(width, height, pixels)
    }

    /** Sets [sum] to the decoded mean of the 2 x 2 of [image] at [left], [top], clamped to its edges. */
    private fun mean(image: RgbaImage, left: Int, top: Int, role: TextureRole, sum: FloatArray) {
        sum.fill(0f)
        for (i in 0 until SAMPLES.toInt()) {
            val sx = minOf(left + i % 2, image.width - 1)
            val sy = minOf(top + i / 2, image.height - 1)
            for (c in 0 until CHANNELS) sum[c] += decoded(image.channel(sx, sy, c), c, role)
        }
        for (c in 0 until CHANNELS) sum[c] /= SAMPLES
        if (role == TextureRole.Normal) renormalise(sum)
    }

    private fun decoded(value: Int, channel: Int, role: TextureRole): Float = when {
        channel == ALPHA -> value / MAX
        role == TextureRole.Color -> SRGB_TO_LINEAR[value]
        role == TextureRole.Normal -> value / MAX * 2f - 1f
        else -> value / MAX
    }

    private fun encoded(value: Float, channel: Int, role: TextureRole): Int {
        val unit = when {
            channel == ALPHA -> value
            role == TextureRole.Color -> linearToSrgb(value)
            role == TextureRole.Normal -> (value + 1f) / 2f
            else -> value
        }
        return (unit.coerceIn(0f, 1f) * MAX).roundToInt()
    }

    /** Scales the xyz of [n], in `-1..1`, back to unit length; a zero vector points straight out. */
    private fun renormalise(n: FloatArray) {
        val length = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
        if (length < EPSILON) {
            n[0] = 0f
            n[1] = 0f
            n[2] = 1f
        } else {
            for (c in 0..2) n[c] /= length
        }
    }

    private fun linearToSrgb(linear: Float): Float =
        if (linear <= SRGB_LINEAR_KNEE) linear * SRGB_SLOPE else SRGB_SCALE * linear.pow(1f / SRGB_GAMMA) - SRGB_OFFSET

    private val SRGB_TO_LINEAR = FloatArray(BYTE + 1) {
        val srgb = it / MAX
        if (srgb <= SRGB_ENCODED_KNEE) srgb / SRGB_SLOPE else ((srgb + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_GAMMA)
    }

    private const val ALPHA = 3
    private const val SAMPLES = 4f
    private const val EPSILON = 1e-6f
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val ALPHA_SHIFT = 24
    private const val SRGB_GAMMA = 2.4f
    private const val SRGB_SLOPE = 12.92f
    private const val SRGB_SCALE = 1.055f
    private const val SRGB_OFFSET = 0.055f
    private const val SRGB_LINEAR_KNEE = 0.0031308f
    private const val SRGB_ENCODED_KNEE = 0.04045f
}
