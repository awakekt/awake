/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

/**
 * Periodic heartbeat sender over a [DirectTransport] that keeps NAT bindings, reverse proxies, and
 * idle session timers alive when a client produces no regular outbound input (for example while
 * standing idle or in a backgrounded browser tab).
 *
 * Scheduling uses [DirectTransport.schedule] with a single reused callback so steady-state ticks
 * allocate no timer closures. Packets are transmitted via [DirectTransport.sendNow] only while
 * [Transport.state] is [ConnectionState.Connected]; when [onlyWhenIdle] is `true`, callers invoke
 * [notifyOutbound] on normal outbound sends so a heartbeat is skipped for any interval in which
 * application traffic already went out.
 *
 * @param transport Target [DirectTransport] whose timer and socket carry the heartbeats.
 * @property intervalMillis Delay in milliseconds between heartbeat checks (must be positive).
 * @property onlyWhenIdle When `true`, suppresses the heartbeat if [notifyOutbound] ran since the last check.
 * @param packet Producer returning the heartbeat payload bytes to transmit.
 */
class TransportKeepAlive(
    private val transport: DirectTransport,
    val intervalMillis: Int = DEFAULT_INTERVAL_MILLIS,
    val onlyWhenIdle: Boolean = false,
    private val packet: () -> ByteArray,
) : AutoCloseable {
    /** Whether [close] has been called to stop future heartbeat scheduling. */
    var isClosed: Boolean = false
        private set

    /** Total heartbeat packets handed to [DirectTransport.sendNow]. */
    var sentCount: Long = 0L
        private set

    private var outboundSinceLastTick: Boolean = false
    private val tick: () -> Unit = ::onTick

    init {
        require(intervalMillis > 0) { "intervalMillis must be positive, was $intervalMillis" }
        transport.schedule(intervalMillis, tick)
    }

    /**
     * Marks that application outbound traffic was sent during the current interval.
     *
     * When [onlyWhenIdle] is `true`, the next scheduled check clears this mark without sending a
     * redundant heartbeat packet.
     */
    fun notifyOutbound() {
        outboundSinceLastTick = true
    }

    /** Stops the heartbeat loop so subsequent timer callbacks neither send nor reschedule. */
    override fun close() {
        isClosed = true
    }

    private fun onTick() {
        if (isClosed) return
        val idle = !onlyWhenIdle || !outboundSinceLastTick
        outboundSinceLastTick = false
        if (idle && transport.state.value is ConnectionState.Connected) {
            transport.sendNow(packet())
            sentCount++
        }
        if (!isClosed) {
            transport.schedule(intervalMillis, tick)
        }
    }

    /** Default configuration constants for [TransportKeepAlive]. */
    companion object {
        /** Default heartbeat interval (15 seconds), comfortably below typical 30-60 s proxy idle timeouts. */
        const val DEFAULT_INTERVAL_MILLIS: Int = 15_000
    }
}

/**
 * Starts a periodic [TransportKeepAlive] over this [DirectTransport] using [packet] to build each
 * heartbeat frame.
 *
 * @param intervalMillis Delay in milliseconds between heartbeat checks.
 * @param onlyWhenIdle When `true`, skips sending if [TransportKeepAlive.notifyOutbound] was called in the interval.
 * @param packet Producer returning the heartbeat packet bytes.
 * @return Active [TransportKeepAlive] handle; call [TransportKeepAlive.close] when tearing down the session.
 */
fun DirectTransport.startKeepAlive(
    intervalMillis: Int = TransportKeepAlive.DEFAULT_INTERVAL_MILLIS,
    onlyWhenIdle: Boolean = false,
    packet: () -> ByteArray,
): TransportKeepAlive = TransportKeepAlive(
    transport = this,
    intervalMillis = intervalMillis,
    onlyWhenIdle = onlyWhenIdle,
    packet = packet,
)

/**
 * Starts a periodic [TransportKeepAlive] over this [DirectTransport] that sends a fixed [packet]
 * payload without per-tick byte array allocation.
 *
 * @param packet Pre-encoded heartbeat payload bytes.
 * @param intervalMillis Delay in milliseconds between heartbeat checks.
 * @param onlyWhenIdle When `true`, skips sending if [TransportKeepAlive.notifyOutbound] was called in the interval.
 * @return Active [TransportKeepAlive] handle; call [TransportKeepAlive.close] when tearing down the session.
 */
fun DirectTransport.startKeepAlive(
    packet: ByteArray,
    intervalMillis: Int = TransportKeepAlive.DEFAULT_INTERVAL_MILLIS,
    onlyWhenIdle: Boolean = false,
): TransportKeepAlive = TransportKeepAlive(
    transport = this,
    intervalMillis = intervalMillis,
    onlyWhenIdle = onlyWhenIdle,
    packet = { packet },
)
