/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import kotlin.math.ceil
import kotlin.math.floor

/**
 * A rectangle of whole pixels, [left] and [top] inclusive, [right] and [bottom] exclusive: the part
 * of a backdrop one blur reads, or that part at one of its levels.
 *
 * @property left The first column.
 * @property top The first row.
 * @property right One past the last column.
 * @property bottom One past the last row.
 */
data class BackdropRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    /** The region's width in pixels. */
    val width: Int get() = right - left

    /** The region's height in pixels. */
    val height: Int get() = bottom - top

    /** True when the region covers no pixel. */
    val isEmpty: Boolean get() = right <= left || bottom <= top

    /**
     * This full-resolution region at [level]: every texel any of its pixels falls in, inside a
     * level of a [width] by [height] backdrop.
     *
     * @param level The number of halvings.
     * @param width The backdrop's full-resolution width.
     * @param height The backdrop's full-resolution height.
     * @return The covering texels at [level].
     */
    fun atLevel(level: Int, width: Int, height: Int): BackdropRegion {
        val scale = DualKawaseBlur.levelScale(level)
        return BackdropRegion(
            left = floor(left / scale).toInt().coerceAtLeast(0),
            top = floor(top / scale).toInt().coerceAtLeast(0),
            right = ceil(right / scale).toInt().coerceAtMost(DualKawaseBlur.levelSize(width, level)),
            bottom = ceil(bottom / scale).toInt().coerceAtMost(DualKawaseBlur.levelSize(height, level)),
        )
    }

    companion object {
        /**
         * The pixels a blur of a shape with bounds [x], [y], [w], [h] reads: the bounds grown by
         * [extent] on every side and clipped to a [width] by [height] backdrop.
         *
         * @return The region, which is empty when the shape lies outside the backdrop.
         */
        @Suppress("LongParameterList")
        fun around(x: Float, y: Float, w: Float, h: Float, extent: Int, width: Int, height: Int): BackdropRegion =
            BackdropRegion(
                left = (floor(x).toInt() - extent).coerceIn(0, width),
                top = (floor(y).toInt() - extent).coerceIn(0, height),
                right = (ceil(x + w).toInt() + extent).coerceIn(0, width),
                bottom = (ceil(y + h).toInt() + extent).coerceIn(0, height),
            )
    }
}

/**
 * The CPU twin of the renderers' backdrop blur: the same passes over the same texels with the same
 * bilinear reads, for a software rasterizer and for checking what a GPU drew.
 *
 * Every array is RGBA, four floats a pixel, rows top to bottom. It allocates its levels on each
 * call, so it is for tests and offline rendering, not a frame loop.
 */
object BackdropBlurReference {
    private const val CHANNELS = 4

    /**
     * Blurs [region] of a [width] by [height] backdrop with [blur].
     *
     * @param pixels The backdrop, RGBA floats.
     * @return The blurred backdrop, read through [BlurredBackdrop.sample].
     */
    fun blur(pixels: FloatArray, width: Int, height: Int, region: BackdropRegion, blur: DualKawaseBlur): BlurredBackdrop {
        require(pixels.size >= width * height * CHANNELS) { "The backdrop holds fewer than $width x $height pixels." }
        val levels = arrayOfNulls<Level>(blur.iterations + 1)
        levels[0] = Level(pixels, width, height)
        for (level in 1..blur.iterations) {
            val source = requireNotNull(levels[level - 1])
            levels[level] = source.down(region.atLevel(level - 1, width, height), region.atLevel(level, width, height), blur.offset)
        }
        for (level in blur.iterations - 1 downTo blur.outputLevel) {
            val source = requireNotNull(levels[level + 1])
            val target = requireNotNull(levels[level])
            source.up(region.atLevel(level + 1, width, height), target, region.atLevel(level, width, height), blur.offset)
        }
        return BlurredBackdrop(requireNotNull(levels[blur.outputLevel]), blur.outputLevel)
    }

    internal class Level(val pixels: FloatArray, val width: Int, val height: Int) {
        /** Bilinear, clamp-to-edge, with the position clamped to texel centres inside [clamp]. */
        fun sample(x: Float, y: Float, clamp: BackdropRegion, out: FloatArray, weight: Float) {
            val px = x.coerceIn(clamp.left + 0.5f, clamp.right - 0.5f) - 0.5f
            val py = y.coerceIn(clamp.top + 0.5f, clamp.bottom - 0.5f) - 0.5f
            val x0 = floor(px).toInt()
            val y0 = floor(py).toInt()
            val fx = px - x0
            val fy = py - y0
            accumulate(x0, y0, (1f - fx) * (1f - fy) * weight, out)
            accumulate(x0 + 1, y0, fx * (1f - fy) * weight, out)
            accumulate(x0, y0 + 1, (1f - fx) * fy * weight, out)
            accumulate(x0 + 1, y0 + 1, fx * fy * weight, out)
        }

        private fun accumulate(x: Int, y: Int, weight: Float, out: FloatArray) {
            if (weight == 0f) return
            val index = (y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)) * CHANNELS
            for (channel in 0 until CHANNELS) out[channel] += pixels[index + channel] * weight
        }

        fun down(sourceRegion: BackdropRegion, targetRegion: BackdropRegion, offset: Float): Level {
            val target = Level(FloatArray(levelWidth() * levelHeight() * CHANNELS), levelWidth(), levelHeight())
            val sum = FloatArray(CHANNELS)
            for (y in targetRegion.top until targetRegion.bottom) {
                for (x in targetRegion.left until targetRegion.right) {
                    sum.fill(0f)
                    val cx = 2f * (x + 0.5f)
                    val cy = 2f * (y + 0.5f)
                    sample(cx, cy, sourceRegion, sum, DOWN_CENTRE)
                    sample(cx - offset, cy - offset, sourceRegion, sum, DOWN_CORNER)
                    sample(cx + offset, cy - offset, sourceRegion, sum, DOWN_CORNER)
                    sample(cx - offset, cy + offset, sourceRegion, sum, DOWN_CORNER)
                    sample(cx + offset, cy + offset, sourceRegion, sum, DOWN_CORNER)
                    sum.copyInto(target.pixels, (y * target.width + x) * CHANNELS)
                }
            }
            return target
        }

        fun up(sourceRegion: BackdropRegion, target: Level, targetRegion: BackdropRegion, offset: Float) {
            val sum = FloatArray(CHANNELS)
            val result = FloatArray(targetRegion.width * targetRegion.height * CHANNELS)
            for (y in targetRegion.top until targetRegion.bottom) {
                for (x in targetRegion.left until targetRegion.right) {
                    sum.fill(0f)
                    val cx = (x + 0.5f) / 2f
                    val cy = (y + 0.5f) / 2f
                    sample(cx + 2f * offset, cy, sourceRegion, sum, UP_EDGE)
                    sample(cx - 2f * offset, cy, sourceRegion, sum, UP_EDGE)
                    sample(cx, cy + 2f * offset, sourceRegion, sum, UP_EDGE)
                    sample(cx, cy - 2f * offset, sourceRegion, sum, UP_EDGE)
                    sample(cx + offset, cy + offset, sourceRegion, sum, UP_DIAGONAL)
                    sample(cx - offset, cy + offset, sourceRegion, sum, UP_DIAGONAL)
                    sample(cx + offset, cy - offset, sourceRegion, sum, UP_DIAGONAL)
                    sample(cx - offset, cy - offset, sourceRegion, sum, UP_DIAGONAL)
                    sum.copyInto(result, ((y - targetRegion.top) * targetRegion.width + x - targetRegion.left) * CHANNELS)
                }
            }
            for (row in 0 until targetRegion.height) {
                result.copyInto(
                    target.pixels,
                    ((targetRegion.top + row) * target.width + targetRegion.left) * CHANNELS,
                    row * targetRegion.width * CHANNELS,
                    (row + 1) * targetRegion.width * CHANNELS,
                )
            }
        }

        private fun levelWidth() = (width + 1) / 2

        private fun levelHeight() = (height + 1) / 2
    }

    private const val DOWN_CENTRE = 4f / 8f
    private const val DOWN_CORNER = 1f / 8f
    private const val UP_EDGE = 1f / 12f
    private const val UP_DIAGONAL = 2f / 12f
}

/**
 * A blurred backdrop at its output level, read the way the glass reads it.
 */
class BlurredBackdrop internal constructor(
    private val level: BackdropBlurReference.Level,
    private val outputLevel: Int,
) {
    /**
     * The blurred colour at full-resolution pixel ([x], [y]), read bilinearly from the output level
     * at the pixel's centre.
     *
     * @param out Receives the RGBA colour.
     */
    fun sample(x: Int, y: Int, out: FloatArray) {
        out.fill(0f)
        val scale = DualKawaseBlur.levelScale(outputLevel)
        val whole = BackdropRegion(0, 0, level.width, level.height)
        level.sample((x + 0.5f) / scale, (y + 0.5f) / scale, whole, out, 1f)
    }
}
