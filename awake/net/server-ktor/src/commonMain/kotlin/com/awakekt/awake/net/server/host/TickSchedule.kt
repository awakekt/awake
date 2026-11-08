/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

/**
 * Fixed-rate simulation deadline tracker with bounded catch-up ([maxBacklogTicks]).
 *
 * Advances deadlines in exact increments of [periodNanos] from [startNanos] so simulation time
 * tracks wall time without accumulating sleep drift, running overdue ticks back-to-back after a
 * transient pause (such as a GC pause or CPU quota hold) while dropping excess backlog beyond
 * [maxBacklogTicks] so a multi-second stall never triggers an unbounded catch-up spiral.
 *
 * Single-threaded; owned and mutated exclusively by the simulation tick thread.
 *
 * @param ticksPerSecond Authoritative tick frequency in Hz (`>= 1`).
 * @property maxBacklogTicks Maximum overdue catch-up ticks retained before excess backlog is dropped (`>= 0`).
 * @param startNanos Initial deadline for the first tick in nanoseconds.
 */
class TickSchedule(
    ticksPerSecond: Int,
    val maxBacklogTicks: Int,
    startNanos: Long,
) {
    init {
        require(ticksPerSecond >= 1) {
            "ticksPerSecond must be >= 1, was $ticksPerSecond."
        }
        require(maxBacklogTicks >= 0) {
            "maxBacklogTicks must be >= 0, was $maxBacklogTicks."
        }
    }

    /** Exact duration of one tick period in nanoseconds (`1_000_000_000L / ticksPerSecond`). */
    val periodNanos: Long = NANOS_PER_SECOND / ticksPerSecond

    /** Monotonic nanosecond timestamp at which the next tick is due. */
    var nextAt: Long = startNanos
        private set

    /**
     * Returns `true` when at least one tick is due at [nowNanos].
     *
     * @param nowNanos Current monotonic clock reading in nanoseconds.
     * @return Whether `nowNanos >= nextAt`.
     */
    fun due(nowNanos: Long): Boolean = nowNanos >= nextAt

    /**
     * Returns how many additional overdue ticks lie behind the next due tick at [nowNanos].
     *
     * @param nowNanos Current monotonic clock reading in nanoseconds.
     * @return Overdue tick count beyond the current tick (`0` when on time or early).
     */
    fun behind(nowNanos: Long): Long =
        if (nowNanos < nextAt) 0L else (nowNanos - nextAt) / periodNanos

    /**
     * Returns the total number of ticks currently due at [nowNanos] (including [nextAt] itself).
     *
     * When called right after [begin], reports how many back-to-back catch-up ticks still remain in
     * the current burst.
     *
     * @param nowNanos Current monotonic clock reading in nanoseconds.
     * @return Total due ticks (`0` when `nowNanos < nextAt`, else `1 + behind(nowNanos)`).
     */
    fun owed(nowNanos: Long): Long =
        if (nowNanos < nextAt) 0L else 1L + behind(nowNanos)

    /**
     * Consumes one due tick at [nowNanos], advancing [nextAt] by one period plus any overdue ticks
     * past [maxBacklogTicks] that are abandoned.
     *
     * @param nowNanos Current monotonic clock reading in nanoseconds.
     * @return Number of overdue ticks dropped because backlog exceeded [maxBacklogTicks].
     */
    fun begin(nowNanos: Long): Long {
        val dropped = (behind(nowNanos) - maxBacklogTicks).coerceAtLeast(0L)
        nextAt += (dropped + 1L) * periodNanos
        return dropped
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
