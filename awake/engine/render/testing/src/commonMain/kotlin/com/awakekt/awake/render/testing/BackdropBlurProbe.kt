/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BackdropBlurQuality
import com.awakekt.awake.core.graphics2d.BackdropBlurReference
import com.awakekt.awake.core.graphics2d.BackdropRegion
import com.awakekt.awake.core.graphics2d.DualKawaseBlur
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A picture of opaque rectangles and frosted-glass panels drawn twice: as [primitives] for a
 * renderer, and on the CPU through [BackdropBlurReference], the oracle a backend's backdrop blur is
 * compared with. Each call paints over what the earlier ones drew, in the order a renderer paints.
 *
 * The CPU glass follows the renderers: the blur `DualKawaseBlur.forRadius` picks for the
 * [quality], over the panel's bounds grown by its extent, then saturation, tint and coverage. Only
 * rounded rectangles without noise or a transform are supported, which is all a probe needs.
 *
 * @property width The picture's width in pixels.
 * @property height The picture's height in pixels.
 * @property quality The quality the renderer under test runs at.
 */
class BackdropBlurProbe(val width: Int, val height: Int, val quality: BackdropBlurQuality = BackdropBlurQuality.High) {
    private val pixels = FloatArray(width * height * CHANNELS)
    private val drawn = ArrayList<UiDrawPrimitive>()

    /** Everything drawn so far, in paint order. */
    val primitives: List<UiDrawPrimitive> get() = drawn

    /** Paints an opaque [color] rectangle. */
    fun fill(x: Int, y: Int, w: Int, h: Int, color: Color) {
        drawn += UiDrawPrimitive.Quad(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color)
        for (row in max(0, y) until min(height, y + h)) {
            for (column in max(0, x) until min(width, x + w)) {
                val index = (row * width + column) * CHANNELS
                pixels[index] = color.r
                pixels[index + 1] = color.g
                pixels[index + 2] = color.b
                pixels[index + 3] = 1f
            }
        }
    }

    /** Paints [panel], blurring what is behind it, or filling it with its fallback at [BackdropBlurQuality.Off]. */
    fun glass(panel: UiDrawPrimitive.BackdropBlur) {
        require(panel.path == null && panel.transform == null && panel.noise == 0f) { "The probe draws plain rounded glass only." }
        drawn += panel
        val blurred = if (quality == BackdropBlurQuality.Off) {
            null
        } else {
            val blur = DualKawaseBlur.forRadius(panel.blurRadius, quality)
            val region = BackdropRegion.around(panel.x, panel.y, panel.w, panel.h, blur.extent, width, height)
            BackdropBlurReference.blur(pixels.copyOf(), width, height, region, blur)
        }
        val sample = FloatArray(CHANNELS)
        for (row in 0 until height) {
            for (column in 0 until width) {
                val coverage = coverage(panel, column, row) * panel.alpha
                if (coverage <= 0f) continue
                if (blurred == null) {
                    val fallback = panel.fallback
                    blend(column, row, floatArrayOf(fallback.r, fallback.g, fallback.b), coverage * fallback.a)
                } else {
                    blurred.sample(column, row, sample)
                    blend(column, row, shade(sample, panel), coverage)
                }
            }
        }
    }

    /**
     * Whether pixel ([x], [y]) is in the antialiased band along a glass panel's edge, where the
     * GPU's derivative-based edge and this probe's may round a step apart.
     */
    fun onGlassEdge(x: Int, y: Int): Boolean = drawn.any { it is UiDrawPrimitive.BackdropBlur && abs(distance(it, x, y)) < EDGE_BAND }

    /**
     * Compares [rgba], a renderer's tightly packed RGBA8 readback of this picture, with the CPU
     * picture, leaving out the panels' edge bands.
     *
     * @return The largest channel difference in 8-bit steps, and where it is.
     */
    fun compare(rgba: ByteArray): ProbeDifference {
        var worst = ProbeDifference(0, 0, 0)
        for (row in 0 until height) {
            for (column in 0 until width) {
                if (onGlassEdge(column, row)) continue
                val index = (row * width + column) * CHANNELS
                for (channel in 0 until RGB) {
                    val expected = (pixels[index + channel].coerceIn(0f, 1f) * MAX_BYTE).roundToInt()
                    val actual = rgba[index + channel].toInt() and BYTE_MASK
                    val difference = abs(expected - actual)
                    if (difference > worst.steps) worst = ProbeDifference(difference, column, row)
                }
            }
        }
        return worst
    }

    /** The CPU picture's 8-bit channel [channel] at ([x], [y]). */
    fun expected(x: Int, y: Int, channel: Int): Int =
        (pixels[(y * width + x) * CHANNELS + channel].coerceIn(0f, 1f) * MAX_BYTE).roundToInt()

    private fun shade(sample: FloatArray, panel: UiDrawPrimitive.BackdropBlur): FloatArray {
        val luma = sample[0] * LUMA_R + sample[1] * LUMA_G + sample[2] * LUMA_B
        val tint = panel.tint
        return FloatArray(RGB) { channel ->
            val saturated = luma + (sample[channel] - luma) * panel.saturation
            val tinted = saturated + (tint.channel(channel) - saturated) * tint.a
            tinted.coerceIn(0f, 1f)
        }
    }

    private fun blend(x: Int, y: Int, color: FloatArray, coverage: Float) {
        val index = (y * width + x) * CHANNELS
        for (channel in 0 until RGB) pixels[index + channel] = color[channel] * coverage + pixels[index + channel] * (1f - coverage)
        pixels[index + 3] = coverage + pixels[index + 3] * (1f - coverage)
    }

    private fun coverage(panel: UiDrawPrimitive.BackdropBlur, x: Int, y: Int): Float {
        val distance = distance(panel, x, y)
        // The shader's smoothstep over one pixel either side of the edge.
        val t = ((distance + 1f) / 2f).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    private fun distance(panel: UiDrawPrimitive.BackdropBlur, x: Int, y: Int): Float {
        val halfW = panel.w / 2f
        val halfH = panel.h / 2f
        val radius = panel.cornerRadius.coerceIn(0f, min(halfW, halfH))
        val qx = abs(x + 0.5f - (panel.x + halfW)) - halfW + radius
        val qy = abs(y + 0.5f - (panel.y + halfH)) - halfH + radius
        val outside = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f))
        return outside + min(max(qx, qy), 0f) - radius
    }

    private fun Color.channel(index: Int): Float = when (index) {
        0 -> r
        1 -> g
        else -> b
    }

    private companion object {
        const val CHANNELS = 4
        const val RGB = 3
        const val MAX_BYTE = 255f
        const val BYTE_MASK = 0xFF
        const val EDGE_BAND = 1.5f
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f
    }
}

/**
 * The largest difference [BackdropBlurProbe.compare] found.
 *
 * @property steps The difference in 8-bit steps.
 * @property x Where, horizontally.
 * @property y Where, vertically.
 */
data class ProbeDifference(val steps: Int, val x: Int, val y: Int)
