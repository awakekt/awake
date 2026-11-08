/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.ceil
import kotlin.math.exp

/**
 * Adaptive client playback clock that maps frame time onto authoritative server time and adjusts
 * remote-entity interpolation [delaySeconds] to observed snapshot cadence and arrival jitter.
 *
 * Unlike [SnapshotTimeline], which holds a fixed interpolation buffer behind an exponentially
 * smoothed offset, [AdaptiveSnapshotTimeline] separates clock-skew tracking from jitter buffering:
 * - A rolling minimum over the last `8` arrival offsets (`clientSeconds - serverSeconds`) filters
 *   out queueing spikes. [advance] slews toward that minimum over [SLEW_SECONDS] (capped at
 *   [MAX_SLEW_RATE]) so playback tracks clock drift smoothly without rewinding or swinging speed on
 *   small minimum shifts.
 * - [intervalSeconds] is learned as the median positive server-time gap across the last `16`
 *   snapshots, keeping single catch-up bursts from distorting the estimated tick cadence.
 * - [jitterSeconds] tracks the 95th-percentile arrival lateness relative to the mapped clock over
 *   the last [JITTER_WINDOW_SECONDS] of server time.
 * - [delaySeconds] targets `(2 * intervalSeconds + jitterSeconds)` clamped to
 *   `minDelaySeconds..maxDelaySeconds`, growing at [GROW_RATE] and shrinking at [SHRINK_RATE] so
 *   interpolation expands gently on noisy links and recovers once steady.
 * - [hold] caps how far playback may run past [newestServerSeconds] during link or server stalls by
 *   extending [delaySeconds]; excess above [maxDelaySeconds] drains back at [CATCH_UP_RATE], and if
 *   the server dropped ticks during the stall ([REBASE_SECONDS]), the offset absorbs the step
 *   immediately from the held delay without jumping or rewinding.
 * - When client frames pause for longer than [PAUSE_REBASE_SECONDS] (such as a backgrounded tab),
 *   [observe] rebases onto the resumed server clock immediately.
 *
 * All windows use fixed primitive ring buffers and allocate zero bytes after construction.
 *
 * @property minDelaySeconds Minimum steady-state interpolation delay in seconds (non-negative).
 * @property maxDelaySeconds Maximum steady-state interpolation delay in seconds (`>= minDelaySeconds`).
 * @property initialIntervalSeconds Assumed snapshot interval in seconds before two snapshots arrive.
 */
class AdaptiveSnapshotTimeline(
    val minDelaySeconds: Double = DEFAULT_MIN_DELAY_SECONDS,
    val maxDelaySeconds: Double = DEFAULT_MAX_DELAY_SECONDS,
    val initialIntervalSeconds: Double = DEFAULT_INITIAL_INTERVAL_SECONDS,
) {
    init {
        require(minDelaySeconds >= 0.0 && minDelaySeconds.isFinite()) {
            "minDelaySeconds must be finite and non-negative, was $minDelaySeconds."
        }
        require(maxDelaySeconds >= minDelaySeconds && maxDelaySeconds.isFinite()) {
            "maxDelaySeconds ($maxDelaySeconds) must be finite and >= minDelaySeconds ($minDelaySeconds)."
        }
        require(initialIntervalSeconds > 0.0 && initialIntervalSeconds.isFinite()) {
            "initialIntervalSeconds must be finite and positive, was $initialIntervalSeconds."
        }
    }

    private val offsets = DoubleArray(OFFSET_WINDOW)
    private var offsetCount = 0
    private var offsetNext = 0
    private var offset = 0.0
    private var pendingPauseRebase = false

    private val lateTimes = DoubleArray(JITTER_CAPACITY)
    private val lateness = DoubleArray(JITTER_CAPACITY)
    private var lateStart = 0
    private var lateCount = 0

    private val gaps = DoubleArray(GAP_WINDOW)
    private var gapCount = 0
    private var gapNext = 0

    private val scratch = DoubleArray(JITTER_CAPACITY)

    /** Learned median snapshot interval in seconds of server time. */
    var intervalSeconds: Double = initialIntervalSeconds
        private set

    /** Observed 95th-percentile snapshot arrival lateness in seconds over [JITTER_WINDOW_SECONDS]. */
    var jitterSeconds: Double = 0.0
        private set

    /** Current interpolation delay held behind [serverTime] in seconds. */
    var delaySeconds: Double = computeTargetDelay()
        private set

    /** Newest server timestamp passed to [observe] in seconds, or `Double.NaN` before [ready]. */
    var newestServerSeconds: Double = Double.NaN
        private set

    /** Whether at least one snapshot has been observed since construction or [reset]. */
    val ready: Boolean
        get() = offsetCount > 0

    /**
     * Records a snapshot carrying [serverSeconds] that reached the client at [clientSeconds].
     *
     * @param clientSeconds Monotonic client timestamp in seconds when the snapshot was received.
     * @param serverSeconds Authoritative server timestamp carried by the snapshot in seconds.
     */
    fun observe(clientSeconds: Double, serverSeconds: Double) {
        require(clientSeconds.isFinite()) {
            "clientSeconds must be finite, was $clientSeconds."
        }
        require(serverSeconds.isFinite()) {
            "serverSeconds must be finite, was $serverSeconds."
        }
        val arrival = clientSeconds - serverSeconds
        if (offsetCount > 0 && arrival - offset < -PAUSE_REBASE_SECONDS) {
            clearWindows()
            pendingPauseRebase = true
        }
        if (offsetCount == 0 || (pendingPauseRebase && arrival < offset)) {
            offset = arrival
        }
        if (!newestServerSeconds.isNaN() && serverSeconds > newestServerSeconds) {
            gaps[gapNext] = serverSeconds - newestServerSeconds
            gapNext = (gapNext + 1) % GAP_WINDOW
            if (gapCount < GAP_WINDOW) gapCount++
            gaps.copyInto(scratch, endIndex = gapCount)
            scratch.sort(0, gapCount)
            intervalSeconds = scratch[(gapCount - 1) / 2]
        }
        if (newestServerSeconds.isNaN() || serverSeconds > newestServerSeconds) {
            newestServerSeconds = serverSeconds
        }
        recordLateness(serverSeconds, arrival - offset)
        offsets[offsetNext] = arrival
        offsetNext = (offsetNext + 1) % OFFSET_WINDOW
        if (offsetCount < OFFSET_WINDOW) offsetCount++
    }

    /**
     * Slews the clock offset and adapts [delaySeconds] across a frame of [deltaSeconds].
     *
     * @param deltaSeconds Non-negative frame duration in seconds.
     */
    fun advance(deltaSeconds: Double) {
        require(deltaSeconds >= 0.0 && deltaSeconds.isFinite()) {
            "deltaSeconds must be finite and non-negative, was $deltaSeconds."
        }
        if (offsetCount > 0) {
            pendingPauseRebase = false
            val limit = deltaSeconds * MAX_SLEW_RATE
            var smallest = offsets[0]
            for (i in 1 until offsetCount) {
                if (offsets[i] < smallest) smallest = offsets[i]
            }
            val step = smallest - offset
            if (step > REBASE_SECONDS && delaySeconds - step >= minDelaySeconds) {
                offset = smallest
                delaySeconds -= step
            } else {
                offset += (step * (1.0 - exp(-deltaSeconds / SLEW_SECONDS))).coerceIn(-limit, limit)
            }
            val shrink = if (delaySeconds > maxDelaySeconds) CATCH_UP_RATE else SHRINK_RATE
            delaySeconds += (computeTargetDelay() - delaySeconds).coerceIn(
                -deltaSeconds * shrink,
                deltaSeconds * GROW_RATE,
            )
        }
    }

    /**
     * Slews the clock offset and adapts [delaySeconds] across a frame of [deltaSeconds].
     *
     * @param deltaSeconds Non-negative frame duration in seconds.
     */
    fun advance(deltaSeconds: Float) {
        advance(deltaSeconds.toDouble())
    }

    /**
     * Prevents [playbackTime] at [clientSeconds] from advancing more than [maxLeadSeconds] ahead of
     * [newestServerSeconds] during a snapshot stall by absorbing any excess into [delaySeconds].
     *
     * @param clientSeconds Current monotonic client timestamp in seconds.
     * @param maxLeadSeconds Maximum extrapolation lead past [newestServerSeconds] in seconds.
     */
    fun hold(clientSeconds: Double, maxLeadSeconds: Double) {
        require(clientSeconds.isFinite()) {
            "clientSeconds must be finite, was $clientSeconds."
        }
        require(maxLeadSeconds >= 0.0 && maxLeadSeconds.isFinite()) {
            "maxLeadSeconds must be finite and non-negative, was $maxLeadSeconds."
        }
        if (!newestServerSeconds.isNaN()) {
            val excess = playbackTime(clientSeconds) - (newestServerSeconds + maxLeadSeconds)
            if (excess > 0.0) {
                delaySeconds += excess
            }
        }
    }

    /**
     * Maps [clientSeconds] onto estimated live server time (`clientSeconds - offset`).
     *
     * @param clientSeconds Monotonic client timestamp in seconds.
     * @return Estimated server timestamp in seconds.
     */
    fun serverTime(clientSeconds: Double): Double = clientSeconds - offset

    /**
     * Maps [clientSeconds] onto remote-entity render playback time (`serverTime - delaySeconds`).
     *
     * @param clientSeconds Monotonic client timestamp in seconds.
     * @return Server-clock timestamp at which remote entities should be interpolated.
     */
    fun playbackTime(clientSeconds: Double): Double = serverTime(clientSeconds) - delaySeconds

    /** Clears all learned offset, interval, and jitter history back to initial defaults. */
    fun reset() {
        offset = 0.0
        pendingPauseRebase = false
        clearWindows()
    }

    private fun computeTargetDelay(): Double =
        (2.0 * intervalSeconds + jitterSeconds).coerceIn(minDelaySeconds, maxDelaySeconds)

    private fun clearWindows() {
        offsetCount = 0
        offsetNext = 0
        gapCount = 0
        gapNext = 0
        lateStart = 0
        lateCount = 0
        newestServerSeconds = Double.NaN
        intervalSeconds = initialIntervalSeconds
        jitterSeconds = 0.0
        delaySeconds = computeTargetDelay()
    }

    private fun recordLateness(serverSeconds: Double, late: Double) {
        if (lateCount == JITTER_CAPACITY) {
            lateStart = (lateStart + 1) % JITTER_CAPACITY
            lateCount--
        }
        val at = (lateStart + lateCount) % JITTER_CAPACITY
        lateTimes[at] = serverSeconds
        lateness[at] = late
        lateCount++
        while (lateCount > 1 && lateTimes[lateStart] < newestServerSeconds - JITTER_WINDOW_SECONDS) {
            lateStart = (lateStart + 1) % JITTER_CAPACITY
            lateCount--
        }
        for (i in 0 until lateCount) {
            scratch[i] = lateness[(lateStart + i) % JITTER_CAPACITY]
        }
        scratch.sort(0, lateCount)
        val index = ceil(JITTER_PERCENTILE * lateCount).toInt().coerceIn(1, lateCount) - 1
        jitterSeconds = scratch[index].coerceAtLeast(0.0)
    }

    /** Default tuning constants for [AdaptiveSnapshotTimeline]. */
    companion object {
        /** Default minimum steady-state interpolation delay (100 ms). */
        const val DEFAULT_MIN_DELAY_SECONDS: Double = 0.1

        /** Default maximum steady-state interpolation delay (250 ms). */
        const val DEFAULT_MAX_DELAY_SECONDS: Double = 0.25

        /** Default initial snapshot interval before two snapshots are observed (50 ms, 20 Hz). */
        const val DEFAULT_INITIAL_INTERVAL_SECONDS: Double = 0.05

        /** Time constant in seconds over which [advance] closes the gap to the rolling minimum offset. */
        const val SLEW_SECONDS: Double = 0.25

        /** Maximum clock-offset slew rate in seconds per second (10%). */
        const val MAX_SLEW_RATE: Double = 0.1

        /** Rate in seconds per second at which [delaySeconds] expands toward higher jitter (5%). */
        const val GROW_RATE: Double = 0.05

        /** Rate in seconds per second at which [delaySeconds] shrinks within steady bounds (2.5%). */
        const val SHRINK_RATE: Double = 0.025

        /** Rate in seconds per second at which stall delay above [maxDelaySeconds] drains back (25%). */
        const val CATCH_UP_RATE: Double = 0.25

        /** Minimum upward shift across all recent offsets before transferring held delay immediately. */
        const val REBASE_SECONDS: Double = 0.1

        /** Client pause jump threshold in seconds beyond which [observe] rebases immediately. */
        const val PAUSE_REBASE_SECONDS: Double = 2.0

        /** Server-time horizon in seconds over which 95th-percentile arrival lateness is measured. */
        const val JITTER_WINDOW_SECONDS: Double = 2.0

        private const val JITTER_PERCENTILE: Double = 0.95
        private const val OFFSET_WINDOW: Int = 8
        private const val GAP_WINDOW: Int = 16
        private const val JITTER_CAPACITY: Int = 64
    }
}
