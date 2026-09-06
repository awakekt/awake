/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Something a connection did that the server refused. Reporting only -- what happens next
 * (kick, ban, shadow-flag, telemetry, thresholds) is the consuming game's policy, so this
 * module names the event and stops there.
 *
 * There is deliberately no interceptor chain around this. Every check runs at one place, in
 * the packet path before input reaches the world, and a pluggable pipeline would add
 * indirection plus a per-packet allocation on the hot path for a single producer.
 */
sealed interface NetViolation {
    val tick: Long

    /** Input addressed an entity the connection does not own. */
    data class NotOwner(override val tick: Long, val target: NetId) : NetViolation

    /** More inputs in one tick than the server accepts. */
    data class InputFlood(override val tick: Long, val count: Int) : NetViolation

    /** A move the server's own simulation would not produce. */
    data class MoveTooFar(override val tick: Long, val meters: Float) : NetViolation

    /** Failed decode: wrong opcode, bad length, truncated, or over-sized. */
    data class MalformedPacket(override val tick: Long, val bytes: Int, val reason: String) : NetViolation

    /** A tick number outside the window the server will accept. */
    data class TickOutOfWindow(override val tick: Long, val serverTick: Long) : NetViolation
}

fun interface ViolationSink {
    fun report(session: SessionId, violation: NetViolation)
}

/**
 * Default sink: counts per session and per kind, and keeps only the first few of each as
 * detail.
 *
 * Logging every violation is itself the denial of service -- an attacker sends malformed
 * packets as fast as the socket allows and the log pipeline becomes the target. Aggregation is
 * therefore the security control, not a tidiness preference. [sample] is bounded by
 * [maxSamplesPerKind]; everything past that increments a counter and allocates nothing new.
 */
class CountingViolationSink(
    private val maxSamplesPerKind: Int = DEFAULT_MAX_SAMPLES,
) : ViolationSink {
    private val counts = mutableMapOf<SessionId, MutableMap<String, Int>>()
    private val samples = mutableMapOf<String, MutableList<NetViolation>>()

    override fun report(session: SessionId, violation: NetViolation) {
        val kind = violation.kind()
        val perSession = counts.getOrPut(session) { mutableMapOf() }
        perSession[kind] = (perSession[kind] ?: 0) + 1

        val kept = samples.getOrPut(kind) { mutableListOf() }
        if (kept.size < maxSamplesPerKind) kept += violation
    }

    fun count(session: SessionId, kind: String): Int = counts[session]?.get(kind) ?: 0

    fun total(): Int = counts.values.sumOf { perSession -> perSession.values.sum() }

    fun sample(kind: String): List<NetViolation> = samples[kind].orEmpty()

    /** Total retained objects, which must stay bounded no matter how long a flood runs. */
    fun retainedSamples(): Int = samples.values.sumOf { it.size }

    fun forget(session: SessionId) {
        counts.remove(session)
    }

    /** One line per session, for the log at session close rather than per packet. */
    fun summary(session: SessionId): String =
        counts[session]?.entries?.sortedBy { it.key }?.joinToString(", ") { "${it.key}=${it.value}" }
            ?: "none"

    private companion object {
        const val DEFAULT_MAX_SAMPLES = 5
    }
}

/** Stable name for counting, independent of the data class's fields. */
fun NetViolation.kind(): String = when (this) {
    is NetViolation.NotOwner -> "not-owner"
    is NetViolation.InputFlood -> "input-flood"
    is NetViolation.MoveTooFar -> "move-too-far"
    is NetViolation.MalformedPacket -> "malformed-packet"
    is NetViolation.TickOutOfWindow -> "tick-out-of-window"
}
