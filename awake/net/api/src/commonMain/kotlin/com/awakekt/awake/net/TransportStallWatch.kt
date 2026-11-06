/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Watches a [DirectTransport] for stalls ([TransportStallDetector]) on the socket's own thread: every
 * [checkMillis] it reads [DirectTransport.bufferedBytes] through [DirectTransport.schedule], so the
 * watch runs while the client sends nothing and doesn't depend on a render frame.
 *
 * The transport hands packets to one [DirectTransport.listen] callback, which belongs to the client,
 * so the client calls [received] from it for each packet. Set [expectInbound] while the peer streams.
 *
 * [state] is the observable signal: a reconnect policy collects it, or reads [state]'s `value` each
 * frame, and acts once [TransportStallState.seconds] passes its own limit. [onEnded] hears each stall
 * as it ends, for diagnostics. The watch stops by itself once the transport disconnects, or on [close].
 */
class TransportStallWatch(
    private val transport: DirectTransport,
    /** How often the socket's buffer is read; a fraction of the shortest threshold. */
    val checkMillis: Int = DEFAULT_CHECK_MILLIS,
    /** The rules and thresholds: owned by this watch, single-threaded on the socket's thread. */
    val detector: TransportStallDetector = TransportStallDetector(),
    /** Monotonic seconds on the socket's thread. */
    private val clockSeconds: () -> Double = monotonicSeconds(),
) : StallWatch {
    private val mutableState = MutableStateFlow(TransportStallState.Clear)
    private val tick: () -> Unit = ::onTick

    /** The transport's stalls as of the last check or packet; [TransportStallState.Clear] while it moves. */
    override val state: StateFlow<TransportStallState> = mutableState.asStateFlow()

    /** Whether [close] ran, or the transport disconnected, so no further checks are scheduled. */
    override var isClosed: Boolean = false
        private set

    /** See [TransportStallDetector.expectInbound]. */
    override var expectInbound: Boolean
        get() = detector.expectInbound
        set(value) {
            detector.expectInbound = value
            mutableState.value = detector.state
        }

    /** See [TransportStallDetector.onEnded]. */
    override var onEnded: ((direction: StallDirection, seconds: Double) -> Unit)?
        get() = detector.onEnded
        set(value) {
            detector.onEnded = value
        }

    init {
        require(checkMillis > 0) { "checkMillis must be positive, was $checkMillis" }
        transport.schedule(checkMillis, tick)
    }

    /** A packet arrived now; call it from the transport's [DirectTransport.listen] callback. */
    override fun received() {
        if (isClosed) return
        detector.received(clockSeconds())
        mutableState.value = detector.state
    }

    /** Stops the checks; [state] keeps its last value. */
    override fun close() {
        isClosed = true
    }

    private fun onTick() {
        if (isClosed) return
        if (transport.state.value is ConnectionState.Disconnected) {
            close()
            return
        }
        mutableState.value = detector.sample(transport.bufferedBytes, clockSeconds())
        transport.schedule(checkMillis, tick)
    }

    /** Default configuration constants for [TransportStallWatch]. */
    companion object {
        /** A quarter second: four readings to the shortest default threshold. */
        const val DEFAULT_CHECK_MILLIS: Int = 250
    }
}

/**
 * Starts a [TransportStallWatch] over this transport. Call [TransportStallWatch.received] for each
 * packet the transport hands on, and [TransportStallWatch.close] when the session ends.
 */
fun DirectTransport.watchStalls(
    checkMillis: Int = TransportStallWatch.DEFAULT_CHECK_MILLIS,
    upstreamAfterSeconds: Double = TransportStallDetector.UPSTREAM_AFTER_SECONDS,
    downstreamAfterSeconds: Double = TransportStallDetector.DOWNSTREAM_AFTER_SECONDS,
): TransportStallWatch = TransportStallWatch(
    transport = this,
    checkMillis = checkMillis,
    detector = TransportStallDetector(upstreamAfterSeconds, downstreamAfterSeconds),
)

internal fun monotonicSeconds(): () -> Double {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().toDouble(DurationUnit.SECONDS) }
}
