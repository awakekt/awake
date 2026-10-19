/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.effects

import com.awakekt.awake.render.pipeline.EffectQuality
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * An integer bounding rectangle in backdrop full-resolution or downsampled texel space.
 *
 * @property left Leftmost texel coordinate (inclusive).
 * @property top Topmost texel coordinate (inclusive).
 * @property right Rightmost texel coordinate (exclusive).
 * @property bottom Bottommost texel coordinate (exclusive).
 */
data class BackdropRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** Width of the region in texels. */
    val width: Int get() = max(0, right - left)

    /** Height of the region in texels. */
    val height: Int get() = max(0, bottom - top)

    /** Whether the region covers zero texels. */
    val isEmpty: Boolean get() = width == 0 || height == 0

    /**
     * Scales this region to the covering texels at downsampled [level] of a [fullWidth] by
     * [fullHeight] backdrop.
     */
    fun atLevel(level: Int, fullWidth: Int, fullHeight: Int): BackdropRegion {
        val scale = BlurChain.levelScale(level)
        return BackdropRegion(
            left = floor(left / scale).toInt().coerceAtLeast(0),
            top = floor(top / scale).toInt().coerceAtLeast(0),
            right = ceil(right / scale).toInt().coerceAtMost(BlurChain.levelSize(fullWidth, level)),
            bottom = ceil(bottom / scale).toInt().coerceAtMost(BlurChain.levelSize(fullHeight, level)),
        )
    }

    companion object {
        /**
         * Computes the bounding region around a shape with bounds [x], [y], [w], [h] grown by
         * [extent] on all sides and clamped to the backdrop of size [width] by [height].
         */
        @Suppress("LongParameterList")
        fun around(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            extent: Int,
            width: Int,
            height: Int,
        ): BackdropRegion = BackdropRegion(
            left = (floor(x).toInt() - extent).coerceIn(0, width),
            top = (floor(y).toInt() - extent).coerceIn(0, height),
            right = (ceil(x + w).toInt() + extent).coerceIn(0, width),
            bottom = (ceil(y + h).toInt() + extent).coerceIn(0, height),
        )
    }
}

/**
 * Mathematical description of a dual-filter (Kawase) blur chain.
 *
 * The chain performs [iterations] downsampling passes, each halving the backdrop and averaging
 * five taps [offset] source pixels apart, followed by [upPasses] upsampling passes of eight taps
 * back up to [outputLevel], which is then sampled by the composite pass.
 *
 * @property iterations Number of halvings from level 0 (at least 1).
 * @property offset Tap distance in source pixels.
 * @property outputLevel Level sampled by the composite pass, or 0 for full resolution (0 to [iterations]).
 */
data class BlurChain(
    val iterations: Int,
    val offset: Float,
    val outputLevel: Int,
) {
    init {
        require(iterations >= 1) { "A dual-filter blur halves at least once." }
        require(outputLevel in 0..iterations) { "Output level must be between 0 and $iterations." }
    }

    /** Number of upsampling passes: from deepest level back up to [outputLevel]. */
    val upPasses: Int get() = iterations - outputLevel

    /** Total number of downsample and upsample passes in this chain. */
    val passCount: Int get() = iterations + upPasses

    /** Standard deviation in full-resolution pixels. */
    val sigma: Float get() = sqrt(variance(iterations, offset, outputLevel))

    /**
     * How far, in full-resolution pixels, a backdrop pixel can travel through the filter chain:
     * the margin around a blurred panel that its blur reads.
     */
    val extent: Int
        get() {
            var reach = 0f
            for (level in 1..iterations) reach += (offset + 1f) * levelScale(level - 1)
            for (level in outputLevel until iterations) reach += (2f * offset + 1f) * levelScale(level + 1)
            return ceil(reach + levelScale(outputLevel)).toInt()
        }

    @Suppress("TooManyFunctions", "ReturnCount")
    companion object {
        const val MIN_OFFSET: Float = 0.5f
        const val PREFERRED_MAX_OFFSET: Float = 2.0f
        const val MAX_OFFSET: Float = 3.0f

        private const val SOLVE_STEPS = 24
        private const val UP_TAP_WEIGHT = 12f
        private val UP_PHASES = floatArrayOf(-0.25f, 0.25f)

        /**
         * Resolves the optimal [BlurChain] for a requested standard deviation [radius] at [quality].
         *
         * Returns `null` if [quality] is [EffectQuality.Off] or [radius] is non-positive.
         */
        fun forRadius(radius: Float, quality: EffectQuality): BlurChain? =
            forRadius(radius, quality, targetOutputLevel = null)

        /**
         * Resolves the optimal [BlurChain] for a requested standard deviation [radius] at [quality]
         * and optional [targetOutputLevel] (or quality tier default when `null`).
         */
        fun forRadius(
            radius: Float,
            quality: EffectQuality,
            targetOutputLevel: Int? = null,
        ): BlurChain? {
            if (quality == EffectQuality.Off || radius <= 0f) return null
            val limits = qualityLimits(quality)
            val targetVariance = radius * radius
            for (iterations in 1..limits.maxIterations) {
                val output = targetOutputLevel ?: min(limits.outputLevel, iterations)
                val last = iterations == limits.maxIterations
                val next = targetOutputLevel ?: min(limits.outputLevel, iterations + 1)
                val gap = !last && variance(iterations + 1, MIN_OFFSET, next) > targetVariance
                val maxOffset = if (last || gap) MAX_OFFSET else PREFERRED_MAX_OFFSET
                if (variance(iterations, maxOffset, output) < targetVariance && !last) continue
                return BlurChain(iterations, solveOffset(iterations, output, targetVariance, maxOffset), output)
            }
            val finalOutput = targetOutputLevel ?: min(limits.outputLevel, limits.maxIterations)
            return BlurChain(limits.maxIterations, MAX_OFFSET, finalOutput)
        }

        /** How many full-resolution pixels one texel of [level] covers along an axis (`2^level`). */
        fun levelScale(level: Int): Float = (1 shl level).toFloat()

        /** Size along one axis of [level] for a full-resolution [size]: each halving rounds up. */
        fun levelSize(size: Int, level: Int): Int {
            var result = max(1, size)
            repeat(level) { result = (result + 1) / 2 }
            return result
        }

        internal fun variance(iterations: Int, offset: Float, outputLevel: Int): Float {
            var total = 0f
            for (level in 1..iterations) total += downVariance(offset) * square(levelScale(level - 1))
            for (level in outputLevel until iterations) total += upVariance(offset) * square(levelScale(level + 1))
            return if (outputLevel == 0) {
                total
            } else {
                total + compositeVariance(outputLevel) * square(levelScale(outputLevel))
            }
        }

        private fun downVariance(offset: Float): Float {
            val taps = offset * offset / 2f
            val bilinear = 0.5f * bilinearVariance(0.5f) + 0.5f * bilinearVariance(offset + 0.5f)
            return taps + bilinear
        }

        private fun upVariance(offset: Float): Float {
            val taps = 4f / 3f * offset * offset
            var bilinear = 0f
            for (phase in UP_PHASES) {
                bilinear += bilinearVariance(phase + 2f * offset) + bilinearVariance(phase - 2f * offset) +
                    2f * bilinearVariance(phase) +
                    4f * bilinearVariance(phase + offset) + 4f * bilinearVariance(phase - offset)
            }
            return taps + bilinear / (UP_TAP_WEIGHT * UP_PHASES.size)
        }

        private fun compositeVariance(outputLevel: Int): Float {
            val scale = levelScale(outputLevel)
            val phases = scale.toInt()
            var sum = 0f
            for (pixel in 0 until phases) sum += bilinearVariance((pixel + 0.5f) / scale - 0.5f)
            return sum / phases
        }

        private fun bilinearVariance(position: Float): Float {
            val fraction = position - floor(position)
            return fraction * (1f - fraction)
        }

        private fun solveOffset(iterations: Int, outputLevel: Int, targetVariance: Float, maxOffset: Float): Float {
            if (variance(iterations, MIN_OFFSET, outputLevel) >= targetVariance) return MIN_OFFSET
            if (variance(iterations, maxOffset, outputLevel) <= targetVariance) return maxOffset
            var low = MIN_OFFSET
            var high = maxOffset
            repeat(SOLVE_STEPS) {
                val middle = (low + high) / 2f
                if (variance(iterations, middle, outputLevel) < targetVariance) low = middle else high = middle
            }
            return (low + high) / 2f
        }

        /** Maximum number of distinct blur operations allowed per frame at [quality]. */
        fun maxBlursPerFrame(quality: EffectQuality): Int = qualityLimits(quality).maxBlursPerFrame

        private fun qualityLimits(quality: EffectQuality): QualityLimits = when (quality) {
            EffectQuality.Off -> QualityLimits(maxIterations = 0, outputLevel = 0, maxBlursPerFrame = 0)
            EffectQuality.Low -> QualityLimits(maxIterations = 4, outputLevel = 2, maxBlursPerFrame = 2)
            EffectQuality.Medium -> QualityLimits(maxIterations = 5, outputLevel = 1, maxBlursPerFrame = 4)
            EffectQuality.High -> QualityLimits(maxIterations = 6, outputLevel = 1, maxBlursPerFrame = 8)
        }
    }
}

private data class QualityLimits(
    val maxIterations: Int,
    val outputLevel: Int,
    val maxBlursPerFrame: Int,
)

private fun square(value: Float): Float = value * value
