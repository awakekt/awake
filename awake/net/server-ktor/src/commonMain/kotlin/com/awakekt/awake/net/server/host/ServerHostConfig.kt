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
 */
data class ServerHostConfig(
    val ticksPerSecond: Int = 30,
    val inboxCapacity: Int = 2048,
    val maxInputsPerTick: Int = 60,
    val stalledNanos: Long = 2_000_000_000L,
)
