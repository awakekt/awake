/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.math.abs
import kotlin.math.ceil

/** What holds a frame to its [FrameRateMode] (#1699). */
enum class FramePacer {
    /** Nothing: the display's vsync, or nothing at all for [FrameRateMode.Unlimited], sets the cadence. */
    None,

    /** The frame loop sleeps out what is left of the frame's budget after its work. */
    Sleep,

    /**
     * The present call holds each image on screen for the swap interval, as a platform frame-pacing
     * library does, so the loop must not also sleep: a frame paced twice runs below its target.
     */
    Present,
}

/** The pacing arithmetic every host shares, kept free of clocks so it is tested without a device. */
object FramePacing {
    private const val NANOS_PER_SECOND = 1_000_000_000L

    /** How far a frame time may sit past a whole number of refreshes and still count as it: 0.1 ms. */
    const val ROUNDING_NANOS = 100_000L

    /**
     * Which pacer runs [mode], given whether the present call can pace ([presentPacing]):
     * [FrameRateMode.Unlimited] is never paced, a cap is paced at present when it can be and slept
     * out otherwise, and [FrameRateMode.Auto] is paced at present when it can be and left to vsync otherwise.
     *
     * A present-time pacer running [FrameRateMode.Auto] may halve the rate when frames run long, as
     * Android's frame-pacing library does by default. Dynamic resolution (#1688) is the first to give way:
     * it lowers the render scale while frames miss, and the rate halves only once the scale is at its
     * floor, so the two never chase the same missed frame.
     */
    fun pacer(mode: FrameRateMode, presentPacing: Boolean): FramePacer = when (mode) {
        FrameRateMode.Unlimited -> FramePacer.None
        FrameRateMode.Auto -> if (presentPacing) FramePacer.Present else FramePacer.None
        is FrameRateMode.Capped -> if (presentPacing) FramePacer.Present else FramePacer.Sleep
    }

    /** A frame's budget at [targetFps], in nanoseconds. */
    fun frameNanos(targetFps: Int): Long {
        require(targetFps > 0) { "targetFps must be positive: $targetFps" }
        return NANOS_PER_SECOND / targetFps
    }

    /**
     * How long the loop sleeps after a frame whose work took [frameWorkNanos]: what is left of a
     * capped frame's budget when [pacer] is [FramePacer.Sleep], and nothing otherwise.
     */
    fun sleepNanos(mode: FrameRateMode, pacer: FramePacer, frameWorkNanos: Long): Long =
        if (pacer == FramePacer.Sleep && mode is FrameRateMode.Capped) {
            (frameNanos(mode.targetFps) - frameWorkNanos).coerceAtLeast(0L)
        } else {
            0L
        }

    /**
     * The swap interval a present-time pacer holds each image for at [targetFps] on a display
     * refreshing every [refreshPeriodNanos]: the fewest whole refreshes, at least one, that do not run
     * faster than the target, since a cap is a ceiling. 60 fps on a 90 Hz panel is two refreshes, 45 fps;
     * on a 120 Hz panel, two refreshes, exactly 60. Periods within [ROUNDING_NANOS] of a whole number of
     * refreshes count as that number, so 16,666,666 ns against a 16,666,667 ns refresh is one.
     */
    fun swapIntervalNanos(targetFps: Int, refreshPeriodNanos: Long): Long {
        require(refreshPeriodNanos > 0) { "refreshPeriodNanos must be positive: $refreshPeriodNanos" }
        val refreshes = ceil((frameNanos(targetFps) - ROUNDING_NANOS).toDouble() / refreshPeriodNanos).toLong().coerceAtLeast(1L)
        return refreshes * refreshPeriodNanos
    }

    /**
     * Of the refresh periods a display offers ([supportedPeriodsNanos]), the one whose swap interval
     * comes nearest [targetFps]'s frame time, the faster period on a tie: 60 fps on a panel offering
     * 90 Hz and 60 Hz picks 60 Hz, and on one offering 120 Hz and 60 Hz picks 120 Hz, which also
     * presents every 16.7 ms and reacts sooner. Returns null when the display offers none.
     */
    fun displayPeriodNanos(targetFps: Int, supportedPeriodsNanos: LongArray): Long? {
        val frame = frameNanos(targetFps)
        var best: Long? = null
        var bestError = Long.MAX_VALUE
        for (period in supportedPeriodsNanos) {
            if (period <= 0) continue
            val error = abs(swapIntervalNanos(targetFps, period) - frame)
            val faster = best != null && period < best
            if (error < bestError || (error == bestError && faster)) {
                best = period
                bestError = error
            }
        }
        return best
    }
}
