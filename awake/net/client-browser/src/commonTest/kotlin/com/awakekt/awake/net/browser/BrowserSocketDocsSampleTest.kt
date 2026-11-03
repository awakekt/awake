/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.browser

import com.awakekt.awake.net.DirectTransport
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The samples in the web networking guide (`website/docs/guides/web-networking.md`): a ping timed with
 * the arrival lag reads the network, not the frame, and a send held back on `bufferedBytes` goes once
 * the socket drains.
 */
class BrowserSocketDocsSampleTest {
    private var nowMillis = 0.0
    private val port = Port()
    private val socket = SocketConnection(port, runsInWorker = true, nowMillis = { nowMillis })

    @Test
    fun aPingTimedWithTheArrivalLagReadsTheNetworkNotTheFrame() {
        val ping = Ping(socket, clock = { nowMillis })
        ping.start()
        ping.send()
        // The reply reaches the worker 40 ms later, but a long frame holds the page for 150 ms more.
        nowMillis = 190.0
        socket.received(PONG, arrivedAtMillis = 40.0)
        assertEquals(40.0, ping.lastMillis, absoluteTolerance = 1e-9)
    }

    @Test
    fun aSendHeldBackOnBufferedBytesGoesOnceTheSocketDrains() {
        val movement = Movement(socket)
        socket.buffered(bufferedAmount = 4_096, takenBytes = 0)
        movement.send(byteArrayOf(1, 2, 3))
        assertEquals(0, port.sent.size, "held back while the socket is full")
        socket.buffered(bufferedAmount = 0, takenBytes = 0)
        port.runScheduled()
        assertEquals(1, port.sent.size)
    }

    // --8<-- [start:ping]
    // Times a ping on any DirectTransport; clock is the page's clock, in milliseconds.
    class Ping(private val transport: DirectTransport, private val clock: () -> Double) {
        private var sentAtMillis = 0.0

        /** The last round trip, in milliseconds. */
        var lastMillis = 0.0
            private set

        fun start() = transport.listen(
            onPacket = { packet ->
                if (packet.contentEquals(PONG)) {
                    // When the reply reached the socket: now, less how long it then waited for this thread.
                    val arrivedAtMillis = clock() - transport.arrivalLag * 1000.0
                    lastMillis = arrivedAtMillis - sentAtMillis
                }
            },
            onClosed = {},
        )

        fun send() {
            sentAtMillis = clock()
            transport.sendNow(PING)
        }
    }
    // --8<-- [end:ping]

    // --8<-- [start:backpressure]
    // Sends movement only while the socket isn't holding much, and tries again a frame later.
    class Movement(private val transport: DirectTransport) {
        fun send(packet: ByteArray) {
            if (transport.bufferedBytes > MAX_BUFFERED_BYTES) {
                transport.schedule(millis = 16) { send(packet) }
            } else {
                transport.sendNow(packet)
            }
        }

        private companion object {
            const val MAX_BUFFERED_BYTES = 1_024L
        }
    }
    // --8<-- [end:backpressure]

    private class Port : SocketPort {
        val sent = ArrayList<ByteArray>()
        private val scheduled = ArrayList<() -> Unit>()

        override fun send(packet: ByteArray) {
            sent += packet
        }

        override fun close() = Unit

        override fun schedule(millis: Int, action: () -> Unit) {
            scheduled += action
        }

        override fun bufferedAmount(): Long = 0L

        fun runScheduled() {
            val due = scheduled.toList()
            scheduled.clear()
            due.forEach { it() }
        }
    }

    private companion object {
        val PING = byteArrayOf(1)
        val PONG = byteArrayOf(2)
    }
}
