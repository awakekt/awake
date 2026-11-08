/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DirectOutboxTest {
    private data class MoveState(val seq: Int, val jump: Boolean = false)

    private fun buildOutbox(socket: FakeDirectSocket): DirectOutbox<MoveState> = DirectOutbox(
        socket = socket,
        encode = { "move ${it.seq}${if (it.jump) " jump" else ""}".encodeToByteArray() },
        merge = { older, newer -> newer.copy(jump = newer.jump || older.jump) },
    )

    @Test
    fun burstWhileBufferFullCollapsesToLatestAndPreservesMergedEdgeFlags() {
        val socket = FakeDirectSocket(buffered = FULL_BYTES)
        val outbox = buildOutbox(socket)

        for (seq in 1..30) {
            outbox.submit(MoveState(seq, jump = seq == 7))
        }
        assertEquals(emptyList(), socket.sent)
        assertEquals(1, outbox.waiting)
        assertEquals(29L, outbox.folded)

        socket.buffered = 0L
        socket.runScheduled()
        assertEquals(listOf("move 30 jump"), socket.sent)
        assertEquals(0, outbox.waiting)
    }

    @Test
    fun oneShotMessagesBypassCongestionInStrictOrderWhileStateWaits() {
        val socket = FakeDirectSocket(buffered = FULL_BYTES)
        val outbox = buildOutbox(socket)
        val expectedOneShots = ArrayList<String>()

        for (i in 1..50) {
            val oneShot = if (i % 10 == 0) "ping $i" else "chat $i"
            expectedOneShots += oneShot
            outbox.sendNow(oneShot.encodeToByteArray())
            outbox.submit(MoveState(i))
        }
        assertEquals(expectedOneShots, socket.sent)

        socket.buffered = 0L
        socket.runScheduled()
        assertEquals(expectedOneShots + "move 50", socket.sent)
    }

    @Test
    fun uncongestedSocketSendsImmediatelyAndRetryReschedulesUntilDrained() {
        val fastSocket = FakeDirectSocket(buffered = 0L)
        val fastOutbox = buildOutbox(fastSocket)
        for (seq in 1..5) fastOutbox.submit(MoveState(seq))
        assertEquals((1..5).map { "move $it" }, fastSocket.sent)
        assertEquals(0, fastOutbox.waiting)
        assertEquals(0, fastSocket.scheduled.size)

        val slowSocket = FakeDirectSocket(buffered = FULL_BYTES)
        val slowOutbox = buildOutbox(slowSocket)
        slowOutbox.submit(MoveState(1))
        slowOutbox.submit(MoveState(2))
        assertEquals(1, slowSocket.scheduled.size)

        slowSocket.runScheduled()
        assertEquals(emptyList(), slowSocket.sent)
        assertEquals(1, slowSocket.scheduled.size)

        slowSocket.buffered = DirectOutbox.BUFFERED_LIMIT
        slowSocket.runScheduled()
        assertEquals(listOf("move 2"), slowSocket.sent)
    }

    @Test
    fun clearDropsPendingStateAndConstructorValidatesBounds() {
        assertFailsWith<IllegalArgumentException> {
            DirectOutbox<MoveState>(FakeDirectSocket(0L), { ByteArray(0) }, { _, n -> n }, limit = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            DirectOutbox<MoveState>(FakeDirectSocket(0L), { ByteArray(0) }, { _, n -> n }, retryMillis = 0)
        }

        val socket = FakeDirectSocket(buffered = FULL_BYTES)
        val outbox = buildOutbox(socket)
        outbox.submit(MoveState(1))
        assertEquals(1, outbox.waiting)
        outbox.clear()
        assertEquals(0, outbox.waiting)
        socket.buffered = 0L
        socket.runScheduled()
        assertEquals(emptyList(), socket.sent)
    }

    private class FakeDirectSocket(var buffered: Long) : DirectTransport {
        val sent = ArrayList<String>()
        val scheduled = ArrayList<() -> Unit>()

        override val bufferedBytes: Long
            get() = buffered

        override val state: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected(SessionId(0L)))

        override fun sendNow(packet: ByteArray) {
            sent += packet.decodeToString()
        }

        override fun listen(onPacket: (ByteArray) -> Unit, onClosed: () -> Unit) = Unit

        override fun schedule(millis: Int, action: () -> Unit) {
            scheduled += action
        }

        fun runScheduled() {
            val due = scheduled.toList()
            scheduled.clear()
            due.forEach { it() }
        }

        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = sendNow(packet)
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() = Unit
    }

    private companion object {
        const val FULL_BYTES = 64_000L
    }
}
