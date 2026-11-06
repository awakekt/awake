/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Watches one connection for stalls ([TransportStallDetector]): [Transport.watchStalls] returns the
 * one that fits the transport, so a client never branches on its kind.
 *
 * The client calls [received] for each packet it reads and sets [expectInbound] while the peer
 * streams. [state] is the signal to observe, and [onEnded] hears each stall as it ends.
 */
interface StallWatch : AutoCloseable {
    /** The connection's stalls as of the last check or packet; [TransportStallState.Clear] while it moves. */
    val state: StateFlow<TransportStallState>

    /** Whether the peer is expected to stream, so its silence is a downstream stall. */
    var expectInbound: Boolean

    /** Hears each stall as it ends, with its direction and length in seconds. */
    var onEnded: ((direction: StallDirection, seconds: Double) -> Unit)?

    /** Whether the watch has stopped, by [close] or because its transport disconnected. */
    val isClosed: Boolean

    /** A packet arrived now; call it from wherever the client reads packets. */
    fun received()
}

/**
 * A [StallWatch] for a transport that isn't a [DirectTransport], such as the Ktor WebSocket client
 * on the JVM and Android. A coroutine on [scope] checks every [checkMillis].
 *
 * Only downstream stalls apply: such a socket can't say how many bytes it holds unsent, so it never
 * stalls upstream. [received] and [expectInbound] may be called from any thread. The ticker alone
 * touches the [detector], so [state] and [onEnded] update on [scope]'s dispatcher.
 */
class PolledStallWatch(
    scope: CoroutineScope,
    /** How often the watch checks; a fraction of the downstream threshold. */
    val checkMillis: Int = TransportStallWatch.DEFAULT_CHECK_MILLIS,
    /** The rules and thresholds: owned by this watch and read on its ticker only. */
    val detector: TransportStallDetector = TransportStallDetector(),
    /** Monotonic seconds, readable from any thread. */
    private val clockSeconds: () -> Double = monotonicSeconds(),
) : StallWatch {
    private val mutableState = MutableStateFlow(TransportStallState.Clear)
    private val ticker: Job

    /** When the last packet arrived, by [clockSeconds], for the ticker to hand on; NaN before the first. */
    @Volatile
    private var receivedAt = Double.NaN

    @Volatile
    private var expecting = false

    override val state: StateFlow<TransportStallState> = mutableState.asStateFlow()

    override var expectInbound: Boolean
        get() = expecting
        set(value) {
            expecting = value
        }

    override var onEnded: ((direction: StallDirection, seconds: Double) -> Unit)?
        get() = detector.onEnded
        set(value) {
            detector.onEnded = value
        }

    override var isClosed: Boolean = false
        private set

    init {
        require(checkMillis > 0) { "checkMillis must be positive, was $checkMillis" }
        ticker = scope.launch { poll() }
    }

    override fun received() {
        receivedAt = clockSeconds()
    }

    /** Stops the ticker; [state] keeps its last value. */
    override fun close() {
        isClosed = true
        ticker.cancel()
    }

    private suspend fun CoroutineScope.poll() {
        var handed = Double.NaN
        while (isActive) {
            delay(checkMillis.toLong())
            detector.expectInbound = expecting
            val arrived = receivedAt
            if (!arrived.isNaN() && arrived != handed) {
                handed = arrived
                detector.received(arrived)
            }
            mutableState.value = detector.sample(null, clockSeconds())
        }
    }
}

/**
 * Starts the [StallWatch] that fits this transport. A [DirectTransport] gets a [TransportStallWatch]
 * on its own thread, which watches both directions. Any other transport gets a [PolledStallWatch] on
 * [scope], which watches downstream only. [clockSeconds] is monotonic and readable from any thread.
 */
fun Transport.watchStalls(
    scope: CoroutineScope,
    checkMillis: Int = TransportStallWatch.DEFAULT_CHECK_MILLIS,
    upstreamAfterSeconds: Double = TransportStallDetector.UPSTREAM_AFTER_SECONDS,
    downstreamAfterSeconds: Double = TransportStallDetector.DOWNSTREAM_AFTER_SECONDS,
    clockSeconds: () -> Double = monotonicSeconds(),
): StallWatch {
    val detector = TransportStallDetector(upstreamAfterSeconds, downstreamAfterSeconds)
    return if (this is DirectTransport) {
        TransportStallWatch(this, checkMillis, detector, clockSeconds)
    } else {
        PolledStallWatch(scope, checkMillis, detector, clockSeconds)
    }
}
