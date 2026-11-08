/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import com.awakekt.awake.net.TransportSession

/**
 * Minimum player contract required by [ServerHost].
 */
interface HostPlayer {
    /** The active transport connection session for this player. */
    val session: TransportSession
}

/**
 * Configuration tuning for [ServerHost].
 *
 * @property ticksPerSecond Authoritative simulation tick frequency (default: 30 Hz).
 * @property inboxCapacity Maximum inbound command queue capacity before rejecting excess inputs.
 * @property maxInputsPerTick Maximum inputs processed from a single player per tick.
 * @property stalledNanos Duration without tick completion before [ServerHost.healthy] reports unhealthy (default: 2 seconds).
 * @property maxBacklogTicks Maximum overdue catch-up ticks run back-to-back after a stall before dropping older backlog (default: 5).
 * @property failuresBeforeUnhealthy Consecutive failed ticks before [ServerHost.healthy] reports unhealthy (default: 3).
 */
data class ServerHostConfig(
    val ticksPerSecond: Int = DEFAULT_TICKS_PER_SECOND,
    val inboxCapacity: Int = DEFAULT_INBOX_CAPACITY,
    val maxInputsPerTick: Int = DEFAULT_MAX_INPUTS_PER_TICK,
    val stalledNanos: Long = DEFAULT_STALLED_NANOS,
    val maxBacklogTicks: Int = DEFAULT_MAX_BACKLOG_TICKS,
    val failuresBeforeUnhealthy: Int = TickHealth.DEFAULT_FAILURES_BEFORE_UNHEALTHY,
) {
    /**
     * Binary-compatible constructor preserving the four-parameter signature from earlier releases.
     *
     * @param ticksPerSecond Authoritative simulation tick frequency.
     * @param inboxCapacity Maximum inbound command queue capacity.
     * @param maxInputsPerTick Maximum inputs processed from a single player per tick.
     * @param stalledNanos Duration without tick completion before reporting unhealthy.
     */
    constructor(
        ticksPerSecond: Int,
        inboxCapacity: Int,
        maxInputsPerTick: Int,
        stalledNanos: Long,
    ) : this(
        ticksPerSecond = ticksPerSecond,
        inboxCapacity = inboxCapacity,
        maxInputsPerTick = maxInputsPerTick,
        stalledNanos = stalledNanos,
        maxBacklogTicks = DEFAULT_MAX_BACKLOG_TICKS,
        failuresBeforeUnhealthy = TickHealth.DEFAULT_FAILURES_BEFORE_UNHEALTHY,
    )

    init {
        require(ticksPerSecond >= 1) {
            "ticksPerSecond must be >= 1, was $ticksPerSecond."
        }
        require(inboxCapacity >= 1) {
            "inboxCapacity must be >= 1, was $inboxCapacity."
        }
        require(maxInputsPerTick >= 1) {
            "maxInputsPerTick must be >= 1, was $maxInputsPerTick."
        }
        require(stalledNanos >= 1L) {
            "stalledNanos must be >= 1, was $stalledNanos."
        }
        require(maxBacklogTicks >= 0) {
            "maxBacklogTicks must be >= 0, was $maxBacklogTicks."
        }
        require(failuresBeforeUnhealthy >= 1) {
            "failuresBeforeUnhealthy must be >= 1, was $failuresBeforeUnhealthy."
        }
    }

    /** Default tuning constants for [ServerHostConfig]. */
    companion object {
        /** Default authoritative simulation tick rate (30 Hz). */
        const val DEFAULT_TICKS_PER_SECOND: Int = 30

        /** Default inbound command channel capacity. */
        const val DEFAULT_INBOX_CAPACITY: Int = 2_048

        /** Default per-player input cap per tick. */
        const val DEFAULT_MAX_INPUTS_PER_TICK: Int = 60

        /** Default stall detection threshold in nanoseconds (2 seconds). */
        const val DEFAULT_STALLED_NANOS: Long = 2_000_000_000L

        /** Default maximum overdue catch-up ticks run back-to-back after a late tick. */
        const val DEFAULT_MAX_BACKLOG_TICKS: Int = 5
    }
}
