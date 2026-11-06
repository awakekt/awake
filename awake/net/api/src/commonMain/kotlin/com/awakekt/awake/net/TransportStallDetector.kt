/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.math.max

/** Which way a stalled connection stopped moving data. */
enum class StallDirection {
    /** The socket holds bytes it has not put on the network, and the pile has stopped falling. */
    Upstream,

    /** Nothing has arrived from the peer for longer than the client expects. */
    Downstream,
}

/**
 * A connection's stalls as of the last reading: how long each direction has been stalled, in
 * seconds, or 0 while that direction moves. A reconnect policy reads [seconds] (for example,
 * "reconnect once [stalled] for over 5 s"); a diagnostics panel reads each direction.
 */
data class TransportStallState(
    /** How long the send buffer has not fallen while stalled upstream; 0 when it isn't. */
    val upstreamSeconds: Double = 0.0,
    /** How long nothing has arrived while stalled downstream; 0 when it isn't. */
    val downstreamSeconds: Double = 0.0,
) {
    /** Whether the send buffer has stalled ([StallDirection.Upstream]). */
    val upstream: Boolean get() = upstreamSeconds > 0.0

    /** Whether inbound traffic has stalled ([StallDirection.Downstream]). */
    val downstream: Boolean get() = downstreamSeconds > 0.0

    /** Whether either direction has stalled. */
    val stalled: Boolean get() = upstream || downstream

    /** The longer of the two stalls going on now, in seconds; 0 when none is. */
    val seconds: Double get() = max(upstreamSeconds, downstreamSeconds)

    /** Shared instances. */
    companion object {
        /** No stall in either direction. */
        val Clear: TransportStallState = TransportStallState()
    }
}

/**
 * Detects a stalled connection from readings the caller takes, with no timers of its own, so any
 * transport can drive it ([TransportStallWatch] drives it on a [DirectTransport]'s own thread).
 *
 * - **Upstream**: the socket holds bytes ([DirectTransport.bufferedBytes] above 0) and that pile has
 *   not fallen for longer than [upstreamAfterSeconds]. A socket that keeps up empties within a frame
 *   or two; one the network can't drain holds its last bytes while new sends pile on top.
 * - **Downstream**: while [expectInbound] is set (the peer streams, for example world snapshots),
 *   nothing has been [received] for longer than [downstreamAfterSeconds]. Off, it never stalls, so a
 *   quiet lobby whose peer only answers keepalives isn't called stalled.
 *
 * A gap between two readings longer than a threshold (a frozen thread, a background tab) says
 * nothing of what happened meanwhile: a wait not yet a stall starts over from the later reading
 * rather than calling the gap a stall, while a stall already going on carries on.
 *
 * When a stall ends, [onEnded] hears its direction and length. Single-threaded: call every member
 * on one thread, as a [DirectTransport]'s members are.
 */
class TransportStallDetector(
    /** How long a send buffer that doesn't fall is an upstream stall; longer than any frame. */
    val upstreamAfterSeconds: Double = UPSTREAM_AFTER_SECONDS,
    /** How long without an inbound packet is a downstream stall, while [expectInbound]. */
    val downstreamAfterSeconds: Double = DOWNSTREAM_AFTER_SECONDS,
    /** Hears each stall as it ends, with its length in seconds. */
    var onEnded: ((direction: StallDirection, seconds: Double) -> Unit)? = null,
) {
    init {
        require(upstreamAfterSeconds > 0.0) { "upstreamAfterSeconds must be positive, was $upstreamAfterSeconds" }
        require(downstreamAfterSeconds > 0.0) { "downstreamAfterSeconds must be positive, was $downstreamAfterSeconds" }
    }

    private var sampledAt = Double.NaN
    private var lastBuffered = 0L
    private var bufferMovedAt = Double.NaN
    private var receivedAt = Double.NaN

    /** The stalls as of the last [sample] or [received]. */
    var state: TransportStallState = TransportStallState.Clear
        private set

    /**
     * Whether the peer is expected to send steadily, so silence is a downstream stall. Turning it on
     * starts the wait from the next reading; turning it off clears a downstream stall unreported.
     */
    var expectInbound: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            receivedAt = Double.NaN
            if (!value) state = state.copy(downstreamSeconds = 0.0)
        }

    /** A packet arrived at [now], in seconds on the caller's clock; ends a downstream stall at once. */
    fun received(now: Double) {
        if (state.downstream) {
            onEnded?.invoke(StallDirection.Downstream, now - receivedAt)
            state = state.copy(downstreamSeconds = 0.0)
        }
        receivedAt = now
    }

    /**
     * The socket held [bufferedBytes] at [now] (null where the transport can't say, which never
     * stalls upstream); returns the stalls as of this reading.
     */
    fun sample(bufferedBytes: Long?, now: Double): TransportStallState {
        val gap = now - sampledAt
        sampledAt = now
        state = TransportStallState(
            upstreamSeconds = upstream(bufferedBytes ?: 0L, now, gap),
            downstreamSeconds = downstream(now, gap),
        )
        return state
    }

    /** Forgets every reading, as for a new connection; a stall going on ends unreported. */
    fun reset() {
        sampledAt = Double.NaN
        lastBuffered = 0L
        bufferMovedAt = Double.NaN
        receivedAt = Double.NaN
        state = TransportStallState.Clear
    }

    private fun upstream(buffered: Long, now: Double, gap: Double): Double {
        val wasStalled = state.upstream
        // A NaN gap (the first reading) is never within the wait.
        val blind = !(gap <= upstreamAfterSeconds) && !wasStalled
        if (blind || buffered <= 0L || buffered < lastBuffered) {
            if (wasStalled) onEnded?.invoke(StallDirection.Upstream, now - bufferMovedAt)
            bufferMovedAt = now
        }
        lastBuffered = buffered
        val held = if (buffered > 0L) now - bufferMovedAt else 0.0
        return if (held > upstreamAfterSeconds) held else 0.0
    }

    private fun downstream(now: Double, gap: Double): Double {
        if (!expectInbound) return 0.0
        val blind = !(gap <= downstreamAfterSeconds) && !state.downstream
        if (receivedAt.isNaN() || (blind && now - receivedAt > downstreamAfterSeconds)) receivedAt = now
        val quiet = now - receivedAt
        return if (quiet > downstreamAfterSeconds) quiet else 0.0
    }

    /** Default thresholds. */
    companion object {
        /** An upstream stall: longer than any frame, and than a link's jitter. */
        const val UPSTREAM_AFTER_SECONDS: Double = 1.0

        /** A downstream stall: dozens of missed packets for a peer streaming at 10 Hz or more. */
        const val DOWNSTREAM_AFTER_SECONDS: Double = 2.0
    }
}
