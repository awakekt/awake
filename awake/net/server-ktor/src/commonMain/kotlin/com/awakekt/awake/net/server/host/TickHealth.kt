/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import kotlin.concurrent.Volatile

/**
 * Lock-free health monitor for an authoritative simulation tick loop, safe to write from the tick
 * thread and read concurrently from HTTP `/health` probes.
 *
 * Reports healthy only once the loop has completed at least one tick, has not stopped ([ended]),
 * finished its latest tick within [stalledAfterNanos], and has fewer than [failuresBeforeUnhealthy]
 * consecutive failed ticks. Uses [TickClock] directly so [ticked] and [healthy] allocate zero
 * boxed primitives on the hot path.
 *
 * @property stalledAfterNanos Maximum elapsed nanoseconds since the last completed tick before reporting stalled (`>= 1`).
 * @property failuresBeforeUnhealthy Consecutive failed ticks required before reporting unhealthy (`>= 1`).
 * @param clock Monotonic nanosecond clock read on each tick and health query.
 */
class TickHealth(
    val stalledAfterNanos: Long,
    val failuresBeforeUnhealthy: Int = DEFAULT_FAILURES_BEFORE_UNHEALTHY,
    private val clock: TickClock = TickClock.Machine,
) {
    init {
        require(stalledAfterNanos >= 1L) {
            "stalledAfterNanos must be >= 1, was $stalledAfterNanos."
        }
        require(failuresBeforeUnhealthy >= 1) {
            "failuresBeforeUnhealthy must be >= 1, was $failuresBeforeUnhealthy."
        }
    }

    @Volatile
    private var tickedAt = 0L

    @Volatile
    private var hasTicked = false

    @Volatile
    private var failedInARow = 0

    @Volatile
    private var stopped = false

    /**
     * Records completion of a simulation tick.
     *
     * @param failed `true` when an unhandled exception occurred during the tick; `false` resets consecutive failure count.
     */
    fun ticked(failed: Boolean = false) {
        failedInARow = if (failed) failedInARow + 1 else 0
        tickedAt = clock.nanoTime()
        hasTicked = true
    }

    /** Marks the tick loop as permanently stopped so health checks fail immediately. */
    fun ended() {
        stopped = true
    }

    /**
     * Returns `true` when [unhealthyReason] is `null`.
     *
     * @return Whether the tick loop is actively and cleanly advancing the world.
     */
    fun healthy(): Boolean = unhealthyReason() == null

    /**
     * Returns a human-readable diagnostic reason when the tick loop is unhealthy, or `null` while healthy.
     *
     * @return Unhealthy diagnostic reason, or `null` if healthy.
     */
    fun unhealthyReason(): String? =
        when {
            stopped -> REASON_STOPPED
            !hasTicked -> REASON_NOT_STARTED
            clock.nanoTime() - tickedAt >= stalledAfterNanos -> REASON_STALLED
            failedInARow >= failuresBeforeUnhealthy -> "the last $failedInARow ticks failed"
            else -> null
        }

    /** Default configuration constants and diagnostic strings for [TickHealth]. */
    companion object {
        /** Default consecutive failed ticks before [healthy] transitions to `false`. */
        const val DEFAULT_FAILURES_BEFORE_UNHEALTHY: Int = 3

        /** Diagnostic reason returned after [ended] is called. */
        const val REASON_STOPPED: String = "the tick loop stopped"

        /** Diagnostic reason returned before the first [ticked] call. */
        const val REASON_NOT_STARTED: String = "the tick loop hasn't ticked yet"

        /** Diagnostic reason returned when `clock.nanoTime() - tickedAt >= stalledAfterNanos`. */
        const val REASON_STALLED: String = "the tick loop stalled"
    }
}
