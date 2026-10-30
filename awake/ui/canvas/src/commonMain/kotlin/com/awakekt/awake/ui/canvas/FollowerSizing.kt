/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How a follower such as a name plate stays crisp and still while its distance and the camera change.
 *
 * - **Size:** a follower is laid out again at its final on-screen size, never stretched. Its scale
 *   curve's value is rounded to one of [BUCKETS_PER_OCTAVE] sizes per doubling, so its text is
 *   re-laid-out only when the size crosses a bucket, at whole-pixel glyph sizes, and a follower
 *   drifting in distance holds its bucket until it is [HYSTERESIS] of a bucket past the edge.
 * - **Place:** while its point moves slower than [SLOW_PX_PER_FRAME] device pixels a frame, its box
 *   lands on whole device pixels, so its glyphs draw the same pixels frame after frame. Faster than
 *   [FAST_PX_PER_FRAME] it moves by the exact amount, where a whole-pixel step would judder and a
 *   moving glyph's fringe is not seen; between the two it keeps what it did last frame.
 * - **Fade:** its opacity multiplies what it draws, with no layer to clip it.
 */
internal object FollowerSizing {
    /** Sizes per doubling: neighbouring sizes differ by 2^(1/16), about 4.4 %. */
    const val BUCKETS_PER_OCTAVE = 16

    /** How far past a bucket's edge, in buckets, a follower's size goes before it moves to the next. */
    const val HYSTERESIS = 0.25f

    /** Slower than this, in device pixels a frame, a follower's box snaps to whole device pixels. */
    const val SLOW_PX_PER_FRAME = 1f

    /** Faster than this, in device pixels a frame, a follower moves by the exact amount. */
    const val FAST_PX_PER_FRAME = 2f

    /** No bucket yet: the first frame takes the nearest one. */
    const val NO_BUCKET = Int.MIN_VALUE

    /** The bucket [scale] falls in, keeping [previous] while [scale] is within its hysteresis. */
    fun bucketOf(scale: Float, previous: Int): Int {
        val exact = log2(scale) * BUCKETS_PER_OCTAVE
        val holds = previous != NO_BUCKET && abs(exact - previous) <= HALF_BUCKET + HYSTERESIS
        return if (holds) previous else exact.roundToInt()
    }

    /** The scale [bucket] draws at: exactly 1 for bucket 0, 0.5 for -16 and 2 for 16. */
    fun scaleOf(bucket: Int): Float = 2f.pow(bucket.toFloat() / BUCKETS_PER_OCTAVE)

    /** Whether a follower that [wasSnapping] last frame snaps this one, having moved [dx], [dy] device pixels. */
    fun snaps(wasSnapping: Boolean, dx: Float, dy: Float): Boolean {
        val moved = sqrt(dx * dx + dy * dy)
        return when {
            moved < SLOW_PX_PER_FRAME -> true
            moved > FAST_PX_PER_FRAME -> false
            else -> wasSnapping
        }
    }

    private const val HALF_BUCKET = 0.5f
}
