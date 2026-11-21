/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.math.roundToInt

/**
 * Picks the steady rate frames run at, from what they cost (#1699): the highest of [candidatesFps]
 * whose frame budget the cost fits, moved only when the cost clearly calls for it.
 *
 * A frame's cost is its own work, not the time it waited for the display: the longer of the CPU's
 * time less its waits on fences and the swapchain, and the GPU's time. It is the same at any target,
 * so frames at 60 say whether 120 would fit.
 *
 * The rule, with p90 the 90th percentile of the costs:
 * - **Start**: after [startFrames] frames, the highest candidate whose budget is at least the p90 of
 *   those frames over [downAt] (95%).
 * - **Step down**: when the p90 of the last [downFrames] frames passes [downAt] of the target's
 *   budget, to the highest candidate that p90 fits.
 * - **Step up**: when the p90 of the last [holdNanos] (2 s) is under [upAt] (75%) of the next faster
 *   candidate's budget, to that candidate, one at a time.
 * - **Hold**: never two changes within [holdNanos], so a cost near a budget can't flap the rate. Between
 *   [upAt] and [downAt] of a budget, nothing moves at all.
 *
 * Pure: it is given each frame's cost and the time, and reads no clock. One instance is for one
 * display's candidates; a display whose rates change gets a new one. Allocates nothing per frame.
 */
class SteadyFrameRate(
    candidatesFps: List<Int>,
    private val startFrames: Int = DEFAULT_WINDOW_FRAMES,
    private val downFrames: Int = DEFAULT_WINDOW_FRAMES,
    private val holdNanos: Long = DEFAULT_HOLD_NANOS,
    private val downAt: Double = DEFAULT_DOWN_AT,
    private val upAt: Double = DEFAULT_UP_AT,
) {
    /** The candidate rates, fastest first. */
    val candidatesFps: List<Int> = candidatesFps.filter { it > 0 }.distinct().sortedDescending()

    init {
        require(this.candidatesFps.isNotEmpty()) { "at least one positive candidate rate is needed: $candidatesFps" }
        require(upAt < downAt) { "upAt ($upAt) must be below downAt ($downAt), or the rate could flap" }
    }

    /** The rate chosen and why; null until [startFrames] frames have been measured. */
    var choice: SteadyRateChoice? = null
        private set

    /** The p90 cost of the last [downFrames] frames, updated every frame: the HUD's live number. */
    var recentCostP90Nanos: Long = 0L
        private set

    private val costs = LongArray(CAPACITY)
    private val times = LongArray(CAPACITY)
    private val scratch = LongArray(CAPACITY)
    private var next = 0
    private var count = 0
    private var changedAt = 0L

    /** Records a frame that cost [costNanos] and ended at [nowNanos]; returns the rate to hold now. */
    fun onFrame(costNanos: Long, nowNanos: Long): SteadyRateChoice? {
        if (costNanos > 0) record(costNanos, nowNanos)
        if (count >= minOf(startFrames, downFrames)) {
            recentCostP90Nanos = p90OfLast(downFrames)
            val current = choice
            when {
                current == null -> startOnceMeasured(nowNanos)
                nowNanos - changedAt >= holdNanos -> move(current.targetFps, nowNanos)
            }
        }
        return choice
    }

    private fun startOnceMeasured(nowNanos: Long) {
        if (count < startFrames) return
        val cost = p90OfLast(startFrames)
        decide(fitting(cost), SteadyRateReason.Start, cost, nowNanos)
    }

    /** Steps down from [target] when the recent frames outgrew it, or up when the whole hold fit the next faster rate. */
    private fun move(target: Int, nowNanos: Long) {
        if (recentCostP90Nanos > downAt * budget(target)) {
            val slower = fitting(recentCostP90Nanos)
            if (slower != target) decide(slower, SteadyRateReason.SteppedDown, recentCostP90Nanos, nowNanos)
            return
        }
        val faster = candidatesFps.lastOrNull { it > target } ?: return
        val held = p90Since(nowNanos - holdNanos)
        if (held < upAt * budget(faster)) decide(faster, SteadyRateReason.SteppedUp, held, nowNanos)
    }

    private fun record(costNanos: Long, nowNanos: Long) {
        costs[next] = costNanos
        times[next] = nowNanos
        next = (next + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    private fun decide(fps: Int, reason: SteadyRateReason, costP90: Long, nowNanos: Long) {
        choice = SteadyRateChoice(fps, reason, costP90, candidatesFps)
        changedAt = nowNanos
    }

    /** The fastest candidate whose budget [costP90] fits within [downAt]; the slowest when none does. */
    private fun fitting(costP90: Long): Int = candidatesFps.firstOrNull { costP90 <= downAt * budget(it) } ?: candidatesFps.last()

    private fun p90OfLast(frames: Int): Long {
        val n = minOf(frames, count)
        for (i in 0 until n) scratch[i] = costs[index(i)]
        return p90(n)
    }

    private fun p90Since(fromNanos: Long): Long {
        var n = 0
        for (i in 0 until count) {
            val at = index(i)
            if (times[at] < fromNanos) break
            scratch[n++] = costs[at]
        }
        return p90(n)
    }

    /** The newest sample is index 0. */
    private fun index(age: Int): Int = (next - 1 - age + CAPACITY) % CAPACITY

    private fun p90(n: Int): Long {
        if (n == 0) return 0L
        scratch.sort(0, n)
        return scratch[((n - 1) * P90).roundToInt()]
    }

    /** The rule's defaults, and the candidate rates a display holds. */
    companion object {
        /** Frames each start and step-down decision reads. */
        const val DEFAULT_WINDOW_FRAMES = 30

        /** The shortest time between two changes, and the span a step up reads: 2 s. */
        const val DEFAULT_HOLD_NANOS = 2_000_000_000L

        /** A target is too fast once the p90 cost passes this share of its budget. */
        const val DEFAULT_DOWN_AT = 0.95

        /** A faster rate is taken once the p90 cost is under this share of its budget. */
        const val DEFAULT_UP_AT = 0.75

        /** The slowest rate a candidate list holds: below it, frames are better left unpaced. */
        const val MIN_FPS = 30

        /** Samples kept: 2 s at 240 fps, with room. */
        private const val CAPACITY = 512
        private const val P90 = 0.9
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        private fun budget(fps: Int): Double = NANOS_PER_SECOND / fps

        /**
         * The rates a display with [refreshRatesHz] holds evenly, fastest first. Its own refresh rates
         * always; with [wholeIntervals], also every rate that shows each frame for a whole number of
         * refreshes, down to [MIN_FPS]: 120, 60, 40 and 30 on a 120 Hz panel. That needs a pacer that
         * holds a frame across refreshes, as Swappy does; vsync alone holds one refresh.
         */
        fun candidates(refreshRatesHz: List<Float>, wholeIntervals: Boolean): List<Int> {
            val rates = refreshRatesHz.map { it.roundToInt() }.filter { it >= MIN_FPS }
            val held = if (!wholeIntervals) {
                rates
            } else {
                rates.flatMap { rate -> (1..rate / MIN_FPS).filter { rate % it == 0 }.map { rate / it } }
            }
            return held.distinct().sortedDescending()
        }
    }
}
