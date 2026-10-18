/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * How much work a renderer spends on [DrawCommand.BackdropBlur], from none to the most.
 *
 * A blur runs at reduced resolution: the backdrop is halved [maxIterations] times at most and the
 * glass samples the level [outputLevel] halvings down. A frame blurs at most [maxBlursPerFrame]
 * times; panels that share a blur count once, and the panels past the limit, like every panel at
 * [Off], fill their shape with their fallback colour instead.
 *
 * @property maxIterations The deepest halving a blur may reach, which bounds its radius.
 * @property outputLevel The halving the glass samples: 1 is half resolution, 2 a quarter.
 * @property maxBlursPerFrame How many separate blurs one frame may run.
 */
enum class BackdropBlurQuality(
    val maxIterations: Int,
    val outputLevel: Int,
    val maxBlursPerFrame: Int,
) {
    /** No blur: every panel is filled with its fallback colour. */
    Off(0, 0, 0),

    /** Quarter-resolution glass, up to four halvings, two blurs a frame. */
    Low(4, 2, 2),

    /** Half-resolution glass, up to five halvings, four blurs a frame. */
    Medium(5, 1, 4),

    /** Half-resolution glass, up to six halvings, eight blurs a frame. */
    High(6, 1, 8),
}

/** The fallback alpha for a fully transparent tint; an opaque tint stays opaque. */
private const val FALLBACK_MIN_ALPHA = 0.6f

/**
 * What a [DrawCommand.BackdropBlur] fills its shape with when it is not blurred: [tint], made more
 * opaque so text on the panel stays readable without the blur behind it.
 *
 * @param tint The glass tint.
 * @return [tint] with its alpha raised to at least 0.6.
 */
fun backdropFallbackColor(tint: Color): Color = tint.withAlpha(FALLBACK_MIN_ALPHA + (1f - FALLBACK_MIN_ALPHA) * tint.a)

/**
 * One dual-filter (Kawase) blur: [iterations] downsampling passes, each halving the backdrop and
 * averaging five taps [offset] source pixels apart, then upsampling passes of eight taps back up
 * to [outputLevel], which the glass samples bilinearly.
 *
 * Both renderers run exactly these passes; [BackdropBlurReference] runs them on the CPU. The
 * downsample at level `k` reads level `k - 1` at the centre `c = 2q + 1` of output texel `q`
 * (source pixels) with weight 4/8 and at `c ± (offset, offset)` with weight 1/8 each. The upsample
 * into level `k` reads level `k + 1` at `c = (q + 0.5) / 2` with taps `(±2o, 0)` and `(0, ±2o)`
 * of weight 1/12 and `(±o, ±o)` of weight 2/12. Every tap is clamped into the region being blurred,
 * so the pixels around it never leak in.
 *
 * @property iterations The number of halvings, from 1.
 * @property offset The tap distance in source pixels.
 * @property outputLevel The level the glass samples, from 1 to [iterations].
 */
data class DualKawaseBlur(val iterations: Int, val offset: Float, val outputLevel: Int) {
    init {
        require(iterations >= 1) { "A dual-filter blur halves at least once." }
        require(outputLevel in 1..iterations) { "The output level must be one of the blur's levels." }
    }

    /** The number of upsampling passes: from the deepest level back up to [outputLevel]. */
    val upPasses: Int get() = iterations - outputLevel

    /** The blur's standard deviation in full-resolution pixels. */
    val sigma: Float get() = sqrt(variance(iterations, offset, outputLevel))

    /**
     * How far, in full-resolution pixels, a backdrop pixel's colour can travel: the margin around a
     * panel that its blur reads.
     */
    val extent: Int
        get() {
            var reach = 0f
            for (level in 1..iterations) reach += (offset + 1f) * levelScale(level - 1)
            for (level in outputLevel until iterations) reach += (2f * offset + 1f) * levelScale(level + 1)
            return ceil(reach + levelScale(outputLevel)).toInt()
        }

    /** The total number of passes, down and up, this blur records. */
    val passCount: Int get() = iterations + upPasses

    companion object {
        /** The smallest tap distance; below it a pass barely blurs. */
        const val MIN_OFFSET = 0.5f

        /** The largest tap distance before a deeper level is used, while one is allowed. */
        const val PREFERRED_MAX_OFFSET = 2f

        /** The largest tap distance at the deepest level a quality allows. */
        const val MAX_OFFSET = 3f

        private const val SOLVE_STEPS = 24

        /**
         * The cheapest blur whose standard deviation is [radius] pixels at [quality]: the fewest
         * halvings that reach it with a tap distance of at most [PREFERRED_MAX_OFFSET], and the tap
         * distance that matches it. A radius past what the quality's deepest level reaches gets
         * that level at [MAX_OFFSET]; a radius below the shallowest blur gets that blur.
         *
         * @param radius The requested standard deviation in full-resolution pixels.
         * @param quality The quality whose limits apply; must not be [BackdropBlurQuality.Off].
         * @return The blur to run.
         */
        fun forRadius(radius: Float, quality: BackdropBlurQuality): DualKawaseBlur {
            require(quality != BackdropBlurQuality.Off) { "BackdropBlurQuality.Off runs no blur." }
            val target = radius.coerceAtLeast(0f)
            val targetVariance = target * target
            for (iterations in 1..quality.maxIterations) {
                val output = minOf(quality.outputLevel, iterations)
                val last = iterations == quality.maxIterations
                // A radius between this level's widest preferred blur and the next level's
                // narrowest stretches this level's taps rather than jumping past it.
                val next = minOf(quality.outputLevel, iterations + 1)
                val gap = !last && variance(iterations + 1, MIN_OFFSET, next) > targetVariance
                val maxOffset = if (last || gap) MAX_OFFSET else PREFERRED_MAX_OFFSET
                if (variance(iterations, maxOffset, output) < targetVariance && !last) continue
                return DualKawaseBlur(iterations, solveOffset(iterations, output, targetVariance, maxOffset), output)
            }
            error("BackdropBlurQuality.${quality.name} allows no halvings.")
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

        /**
         * The per-axis variance of the whole chain in full-resolution pixels squared: each pass's
         * taps, each bilinear read at the phase it is taken, and the glass's final bilinear read.
         */
        internal fun variance(iterations: Int, offset: Float, outputLevel: Int): Float {
            var total = 0f
            for (level in 1..iterations) total += downVariance(offset) * square(levelScale(level - 1))
            for (level in outputLevel until iterations) total += upVariance(offset) * square(levelScale(level + 1))
            return total + compositeVariance(outputLevel) * square(levelScale(outputLevel))
        }

        /** A downsample in source pixels: taps 1/2 at 0 and 1/4 at ±o per axis, read at pixel corners. */
        private fun downVariance(offset: Float): Float {
            val taps = offset * offset / 2f
            val bilinear = 0.5f * bilinearVariance(0.5f) + 0.5f * bilinearVariance(offset + 0.5f)
            return taps + bilinear
        }

        /** An upsample in source pixels: per axis 1/12 at ±2o, 2/12 at 0 and 4/12 at ±o, read a quarter pixel off. */
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

        /** The glass's bilinear read of level [outputLevel] at full-resolution pixel centres, averaged over phase. */
        private fun compositeVariance(outputLevel: Int): Float {
            val scale = levelScale(outputLevel)
            val phases = scale.toInt()
            var sum = 0f
            for (pixel in 0 until phases) sum += bilinearVariance((pixel + 0.5f) / scale - 0.5f)
            return sum / phases
        }

        /** The spread a bilinear read adds at [position] texels from a texel centre. */
        private fun bilinearVariance(position: Float): Float {
            val fraction = position - floor(position)
            return fraction * (1f - fraction)
        }

        private val UP_PHASES = floatArrayOf(-0.25f, 0.25f)
        private const val UP_TAP_WEIGHT = 12f

        /**
         * How many full-resolution pixels one texel of [level] covers along an axis.
         *
         * @param level The number of halvings, 0 for the backdrop itself.
         * @return `2^level`.
         */
        fun levelScale(level: Int): Float = (1 shl level).toFloat()

        /**
         * The size along one axis of [level] for a backdrop [size] pixels long: each halving
         * rounds up, so the last texel covers the remainder.
         *
         * @param size The full-resolution size in pixels.
         * @param level The number of halvings.
         * @return The level's size in its own texels, at least 1.
         */
        fun levelSize(size: Int, level: Int): Int {
            var result = size.coerceAtLeast(1)
            repeat(level) { result = (result + 1) / 2 }
            return result
        }
    }
}

private fun square(value: Float): Float = value * value
