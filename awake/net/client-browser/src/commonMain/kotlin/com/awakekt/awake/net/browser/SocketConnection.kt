/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.browser

import com.awakekt.awake.net.ConnectionState
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Packets a connection keeps for a reader that hasn't taken them yet; later ones are dropped. */
internal const val INBOUND_PACKETS = 256

/**
 * The page's side of a browser socket, whether the socket runs in a worker or on the page: the
 * wasmJs code feeds it the socket's events ([received], [buffered], [closed]) and it hands them on.
 *
 * - Each packet goes to the [port] at once; nothing on the page holds it.
 * - Each packet comes with when it reached the socket, so [arrivalLag] is how long it then waited
 *   for the page. A packet the page's own socket hands on has no wait.
 * - In a worker, [bufferedBytes] is the worker's last reported `bufferedAmount` plus the bytes posted
 *   since that the worker hasn't taken yet, so it is never less than the socket holds.
 * - What came before the close is handed on first, then the close, once.
 *
 * Page thread only. [nowMillis] is the page's clock on the worker's time base: milliseconds since the
 * Unix epoch, `performance.timeOrigin + performance.now()` in a browser, since a dedicated worker's
 * `performance.now()` counts from the worker's own creation, not the page's.
 */
@Suppress("TooManyFunctions") // The transport's members, plus one for each of the socket's three events.
internal class SocketConnection(
    private val port: SocketPort,
    override val runsInWorker: Boolean,
    private val nowMillis: () -> Double,
) : BrowserSocketTransport {
    private val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected(SessionId(0)))

    /** Packets that came before [listen], or for a reader of [incoming]. */
    private val inbound = Channel<ByteArray>(INBOUND_PACKETS)

    /** When each packet waiting in [inbound] for [listen] arrived; not kept once [incoming] is read instead. */
    private val earlyArrivals = ArrayDeque<Double>()
    private var streamed = false
    private var listener: ((ByteArray) -> Unit)? = null
    private var closedListener: (() -> Unit)? = null
    private var open = true

    /** Bytes of the packets posted to the worker since the socket opened. */
    private var posted = 0L

    /** Of [posted], the bytes the worker last said it had taken: sent, or dropped once the socket closed. */
    private var taken = 0L

    /** The socket's `bufferedAmount` the worker last reported. */
    private var reportedBuffered = 0L

    override var arrivalLag: Double = 0.0
        private set

    override var closeCode: Int? = null
        private set

    override var closeReason: String = ""
        private set

    override val state: StateFlow<ConnectionState> = connection

    override val bufferedBytes: Long
        get() = when {
            !open -> 0L
            runsInWorker -> reportedBuffered + (posted - taken).coerceAtLeast(0L)
            else -> port.bufferedAmount()
        }

    override fun sendNow(packet: ByteArray) {
        if (!open) return
        posted += packet.size
        port.send(packet)
    }

    override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = sendNow(packet)

    override fun listen(onPacket: (ByteArray) -> Unit, onClosed: () -> Unit) {
        while (true) {
            val packet = inbound.tryReceive().getOrNull() ?: break
            deliver(onPacket, packet, earlyArrivals.removeFirstOrNull() ?: NO_WAIT)
        }
        listener = onPacket
        closedListener = onClosed
        if (!open) onClosed()
    }

    override fun schedule(millis: Int, action: () -> Unit) = port.schedule(millis, action)

    override fun incoming(): ReceiveChannel<ByteArray> {
        streamed = true
        earlyArrivals.clear()
        return inbound
    }

    override suspend fun close() {
        port.close()
        ended()
    }

    /**
     * A binary message: [packet], which reached the socket at [arrivedAtMillis] on [nowMillis]'s base,
     * or [NO_WAIT] when the socket's own event hands it on.
     */
    fun received(packet: ByteArray, arrivedAtMillis: Double = NO_WAIT) {
        if (!open) return
        val reader = listener
        if (reader != null) {
            deliver(reader, packet, arrivedAtMillis)
        } else if (inbound.trySend(packet).isSuccess && !streamed) {
            earlyArrivals.addLast(arrivedAtMillis)
        }
    }

    /** The worker's report: the socket's `bufferedAmount`, and the bytes of posted packets the worker has taken. */
    fun buffered(bufferedAmount: Long, takenBytes: Long) {
        reportedBuffered = bufferedAmount
        taken = takenBytes
    }

    /** The socket closed with [code] and [reason], or failed. The first report stands. */
    fun closed(code: Int, reason: String) {
        if (closeCode == null) {
            closeCode = code
            closeReason = reason
        }
        ended()
    }

    private fun deliver(reader: (ByteArray) -> Unit, packet: ByteArray, arrivedAtMillis: Double) {
        arrivalLag = if (arrivedAtMillis.isNaN()) 0.0 else ((nowMillis() - arrivedAtMillis) / MILLIS_PER_SECOND).coerceAtLeast(0.0)
        try {
            reader(packet)
        } finally {
            arrivalLag = 0.0
        }
    }

    private fun ended() {
        if (!open) return
        open = false
        connection.value = ConnectionState.Disconnected
        reportedBuffered = 0
        taken = posted
        inbound.close()
        closedListener?.invoke()
    }

    companion object {
        /** The arrival time of a packet the socket's own event hands on: it waited for nothing. */
        const val NO_WAIT = Double.NaN

        private const val MILLIS_PER_SECOND = 1000.0
    }
}

/** The page's handle on the socket, as [SocketConnection] uses it: in a worker, on the page, or a fake in a test. */
internal interface SocketPort {
    /** Sends [packet]: on the page's socket at once, or posted to the worker, which sends it as its next task. */
    fun send(packet: ByteArray)

    /** Asks the socket to close (code 1000); its close event then reports the close. */
    fun close()

    /** Runs [action] once, about [millis] from now, on the page's thread. */
    fun schedule(millis: Int, action: () -> Unit)

    /** The page's socket's `bufferedAmount`, read now. Not called for a socket in a worker, which reports it instead. */
    fun bufferedAmount(): Long
}
