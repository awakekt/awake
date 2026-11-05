/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransportKeepAliveTest {
    private class FakeDirectTransport(
        initialState: ConnectionState = ConnectionState.Connected(SessionId(1L)),
    ) : DirectTransport {
        override val state = MutableStateFlow(initialState)
        override val bufferedBytes: Long = 0L
        val sentPackets = mutableListOf<ByteArray>()
        val scheduled = ArrayDeque<Pair<Int, () -> Unit>>()

        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
            sentPackets += packet
        }

        override fun incoming(): ReceiveChannel<ByteArray> = Channel()

        override suspend fun close() {
            state.value = ConnectionState.Disconnected
        }

        override fun sendNow(packet: ByteArray) {
            sentPackets += packet
        }

        override fun listen(onPacket: (ByteArray) -> Unit, onClosed: () -> Unit) = Unit

        override fun schedule(millis: Int, action: () -> Unit) {
            scheduled.addLast(millis to action)
        }

        fun runNextTimer(): (() -> Unit)? {
            val entry = scheduled.removeFirstOrNull() ?: return null
            entry.second.invoke()
            return entry.second
        }
    }

    @Test
    fun rejectsNonPositiveInterval() {
        val transport = FakeDirectTransport()
        assertFailsWith<IllegalArgumentException> {
            transport.startKeepAlive(intervalMillis = 0) { byteArrayOf(1) }
        }
    }

    @Test
    fun sendsOnlyWhileConnectedAndReusesSingleTimerCallback() {
        val transport = FakeDirectTransport(initialState = ConnectionState.Connecting)
        val heartbeat = byteArrayOf(0x7F)
        val keepAlive = transport.startKeepAlive(
            packet = heartbeat,
            intervalMillis = INTERVAL_MS,
        )

        assertEquals(1, transport.scheduled.size)
        assertEquals(INTERVAL_MS, transport.scheduled.first().first)

        // While Connecting, timer reschedules without sending.
        val firstCallback = transport.runNextTimer()
        assertEquals(0, transport.sentPackets.size)
        assertEquals(0L, keepAlive.sentCount)
        assertEquals(1, transport.scheduled.size)

        // Once Connected, each timer sends the fixed payload and reuses the same callback.
        transport.state.value = ConnectionState.Connected(SessionId(7L))
        val secondCallback = transport.runNextTimer()
        assertSame(firstCallback, secondCallback)
        assertEquals(1, transport.sentPackets.size)
        assertSame(heartbeat, transport.sentPackets.single())
        assertEquals(1L, keepAlive.sentCount)

        // Closing prevents any queued timer from sending or rescheduling.
        keepAlive.close()
        assertTrue(keepAlive.isClosed)
        transport.runNextTimer()
        assertEquals(1, transport.sentPackets.size)
        assertTrue(transport.scheduled.isEmpty())
    }

    @Test
    fun onlyWhenIdleSkipsIntervalsWithOutboundTraffic() {
        val transport = FakeDirectTransport()
        var seq = 0
        val keepAlive = transport.startKeepAlive(
            intervalMillis = INTERVAL_MS,
            onlyWhenIdle = true,
        ) {
            byteArrayOf((++seq).toByte())
        }
        assertFalse(keepAlive.isClosed)

        // Outbound traffic sent during interval 1 -> skipped.
        keepAlive.notifyOutbound()
        transport.runNextTimer()
        assertEquals(0, transport.sentPackets.size)

        // Quiet during interval 2 -> heartbeat sent.
        transport.runNextTimer()
        assertEquals(1, transport.sentPackets.size)
        assertEquals(1.toByte(), transport.sentPackets[0][0])

        // Quiet during interval 3 -> heartbeat sent again.
        transport.runNextTimer()
        assertEquals(2, transport.sentPackets.size)
        assertEquals(2.toByte(), transport.sentPackets[1][0])

        keepAlive.close()
    }

    @Test
    fun unconditionalModeSendsEvenAfterNotifyOutbound() {
        val transport = FakeDirectTransport()
        transport.startKeepAlive(intervalMillis = INTERVAL_MS, onlyWhenIdle = false) {
            byteArrayOf(1)
        }.use { keepAlive ->
            keepAlive.notifyOutbound()
            transport.runNextTimer()
            assertEquals(1, transport.sentPackets.size)
            assertEquals(1L, keepAlive.sentCount)
        }
    }

    private companion object {
        const val INTERVAL_MS = 5_000
    }
}
