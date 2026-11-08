/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Server-time token bucket governing how many client movement or command inputs a connection may
 * submit per second, with bounded credit for network stalls and server-side tick lag ([TickLag]).
 *
 * Counting inputs strictly per server tick falsely penalizes honest clients whenever packets bunch
 * across Wi-Fi retransmits, proxy stalls, or late server ticks. `InputBudget` refills at [perSecond]
 * tokens per second of monotonic server time up to [burst] (widened by `lagNanos` when the server's
 * own loop fell behind). When an arrival gap refills past that ceiling, up to [stallSeconds] worth
 * of excess refill is held aside as gap credit and granted only to strictly advancing client
 * sequence numbers (`seq`), so bunched inputs sent during a tunnel/Wi-Fi pause are accepted while
 * unnumbered or replayed floods gain no extra allowance.
 *
 * Not thread-safe; intended to be owned per connection on a single reader or tick thread.
 *
 * @property perSecond Steady-state token refill rate per second (must be positive and finite).
 * @property burst Maximum token capacity during normal play (must be at least `1.0` and finite).
 * @property stallSeconds Maximum seconds of excess refill retained across a silent network gap for
 *   strictly advancing `seq` numbers (`0.0` disables gap credit).
 */
class InputBudget(
    val perSecond: Double,
    val burst: Double,
    val stallSeconds: Double = 0.0,
) {
    init {
        require(perSecond > 0.0 && perSecond.isFinite()) {
            "perSecond must be finite and > 0, was $perSecond"
        }
        require(burst >= 1.0 && burst.isFinite()) {
            "burst must be finite and >= 1.0, was $burst"
        }
        require(stallSeconds >= 0.0 && stallSeconds.isFinite()) {
            "stallSeconds must be finite and >= 0.0, was $stallSeconds"
        }
    }

    private var tokens: Double = burst
    private var hasLastAt: Boolean = false
    private var lastAtNanos: Long = 0L
    private var gap: Double = 0.0
    private var lastSeq: Long = 0L

    /**
     * Attempts to spend one input token at monotonic server timestamp [nanos].
     *
     * @param nanos Monotonic server arrival timestamp in nanoseconds.
     * @param lagNanos Cumulative server loop lag in nanoseconds (for example [TickLag.lagNanos])
     *   widening the burst ceiling while the server itself is behind schedule.
     * @param seq Optional client-assigned input sequence number (`0` when unnumbered); strictly
     *   increasing sequence numbers may draw from [stallSeconds] gap credit when [burst] is exhausted.
     * @return `true` if one token was spent, or `false` (spending nothing) if the budget is dry.
     */
    fun take(nanos: Long, lagNanos: Long = 0L, seq: Long = 0L): Boolean {
        val most = burst + lagNanos.coerceAtLeast(0L) / NANOS_PER_SECOND * perSecond
        if (hasLastAt) {
            val refilled = tokens + (nanos - lastAtNanos).coerceAtLeast(0L) / NANOS_PER_SECOND * perSecond
            if (refilled > most) {
                gap = minOf(refilled - most, stallSeconds * perSecond)
            }
            tokens = minOf(most, refilled)
            lastAtNanos = maxOf(lastAtNanos, nanos)
        } else {
            hasLastAt = true
            lastAtNanos = nanos
        }
        val next = seq > lastSeq
        lastSeq = maxOf(lastSeq, seq)
        return when {
            tokens >= 1.0 -> {
                tokens -= 1.0
                true
            }
            next && gap >= 1.0 -> {
                gap -= 1.0
                true
            }
            else -> false
        }
    }

    /** Resets the tracked client sequence baseline to `0` (for example across a session or world transfer). */
    fun resetSeq() {
        lastSeq = 0L
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/**
 * Sliding-window tracker measuring how far a fixed-rate simulation loop fell behind its schedule
 * over the most recent [window] ticks.
 *
 * Each tick that starts later than one nominal period (`1_000_000_000L / ticksPerSecond`) after the
 * previous tick records the excess lateness in nanoseconds; back-to-back catch-up ticks start
 * within a period and therefore record zero additional lateness. [lagNanos] exposes the window's
 * summed lateness for [InputBudget.take], while [allowance] scales per-tick discrete request caps
 * by [longestNanos] (the single worst tick stall in the window) so steady clients are not kicked
 * when server pauses bunch packets into one tick, without multiplying sustained per-tick limits
 * across mildly loaded ticks.
 *
 * Not thread-safe; call from the simulation tick thread only.
 *
 * @property ticksPerSecond Nominal simulation tick frequency in Hz (`1..1_000_000_000`).
 * @property window Number of recent ticks retained in the sliding lateness window (at least `1`).
 */
class TickLag(
    val ticksPerSecond: Int,
    val window: Int,
) {
    init {
        require(ticksPerSecond in 1..MAX_TICKS_PER_SECOND) {
            "ticksPerSecond must be in 1..$MAX_TICKS_PER_SECOND, was $ticksPerSecond"
        }
        require(window >= 1) { "window must be >= 1, was $window" }
    }

    private val periodNanos: Long = 1_000_000_000L / ticksPerSecond
    private val late = LongArray(window)
    private var next: Int = 0
    private var hasLast: Boolean = false
    private var lastNanos: Long = 0L

    /** Sum of positive tick-start lateness over the last [window] ticks, in nanoseconds. */
    var lagNanos: Long = 0L
        private set

    /** Maximum single-tick start lateness observed within the last [window] ticks, in nanoseconds. */
    var longestNanos: Long = 0L
        private set

    /**
     * Computes the effective per-tick request allowance for a discrete action whose nominal cap is
     * [perTick] when the loop keeps time.
     *
     * Scales [perTick] by `longestNanos / periodNanos` so requests accumulated during the worst
     * single stall in the window are accepted once, saturating at [Int.MAX_VALUE].
     *
     * @param perTick Nominal per-tick request limit while ticks run on schedule (must be non-negative).
     * @return Effective per-tick request limit accounting for [longestNanos].
     */
    fun allowance(perTick: Int): Int {
        require(perTick >= 0) { "perTick must be non-negative, was $perTick" }
        return (perTick + perTick.toLong() * longestNanos / periodNanos)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    /**
     * Records that a simulation tick began at monotonic timestamp [nanos].
     *
     * @param nanos Monotonic server timestamp in nanoseconds at the start of the tick.
     */
    fun ticked(nanos: Long) {
        val behind = if (hasLast) (nanos - lastNanos - periodNanos).coerceAtLeast(0L) else 0L
        hasLast = true
        lastNanos = maxOf(lastNanos, nanos)
        lagNanos += behind - late[next]
        late[next] = behind
        next = (next + 1) % late.size
        var maxLate = 0L
        for (sample in late) {
            if (sample > maxLate) maxLate = sample
        }
        longestNanos = maxLate
    }

    private companion object {
        const val MAX_TICKS_PER_SECOND = 1_000_000_000
    }
}
