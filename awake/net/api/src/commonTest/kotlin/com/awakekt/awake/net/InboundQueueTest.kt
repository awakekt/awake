/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboundQueueTest {
    @Test
    fun holdsUpToCapacityAndFreesConsumedPackets() {
        val queue = InboundQueue(capacity = 3, byteCapacity = Long.MAX_VALUE)
        repeat(3) { assertTrue(queue.offer(PACKET_BYTES)) }
        queue.taken(PACKET_BYTES)
        assertTrue(queue.offer(PACKET_BYTES))
        assertEquals(3, queue.size)
        assertEquals(3L * PACKET_BYTES, queue.bytes)
        assertFalse(queue.overflowed)
    }

    @Test
    fun exceedingPacketCapacityLatchesOverflowPermanently() {
        val queue = InboundQueue(capacity = 2, byteCapacity = Long.MAX_VALUE)
        repeat(2) { assertTrue(queue.offer(PACKET_BYTES)) }
        assertFalse(queue.offer(PACKET_BYTES))
        assertTrue(queue.overflowed)
        assertEquals(2, queue.size)

        queue.taken(PACKET_BYTES)
        assertFalse(queue.offer(PACKET_BYTES))
        assertEquals(1, queue.size)
    }

    @Test
    fun exceedingByteCapacityLatchesOverflowAndRevertsReservation() {
        val queue = InboundQueue(capacity = 100, byteCapacity = 3L * PACKET_BYTES)
        repeat(3) { assertTrue(queue.offer(PACKET_BYTES)) }
        queue.taken(PACKET_BYTES)
        assertTrue(queue.offer(PACKET_BYTES))
        assertFalse(queue.offer(1))
        assertTrue(queue.overflowed)
        assertEquals(3L * PACKET_BYTES, queue.bytes)

        val huge = InboundQueue(capacity = 200, byteCapacity = 192_000L)
        var held = 0
        while (huge.offer(HUGE_PACKET_BYTES)) held++
        assertTrue(huge.overflowed)
        assertEquals(3, held)
    }

    @Test
    fun cutOffLatchesOverflowAndRejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { InboundQueue(-1, 100L) }
        assertFailsWith<IllegalArgumentException> { InboundQueue(10, -1L) }

        val queue = InboundQueue(capacity = 4, byteCapacity = 1_024L)
        assertFailsWith<IllegalArgumentException> { queue.offer(-1) }
        assertFailsWith<IllegalArgumentException> { queue.taken(-1) }

        queue.cutOff()
        assertTrue(queue.overflowed)
        assertFalse(queue.offer(1))
    }

    @Test
    fun overflowedTransportInboxEndsReadingImmediatelyWithInboxFull() = runTest {
        var dropped = 0L
        val inbox = Channel<ByteArray>(INBOX_CAPACITY, BufferOverflow.DROP_OLDEST) { dropped++ }
        repeat(2 * INBOX_CAPACITY) { assertTrue(inbox.trySend(byteArrayOf(it.toByte())).isSuccess) }
        inbox.close()
        var read = 0

        assertEquals(INBOX_CAPACITY.toLong(), dropped)
        assertEquals(ReadEnd.InboxFull, inbox.readEach({ dropped }) { read++; true })
        assertEquals(0, read)
    }

    @Test
    fun fullInboxWithoutDropsAndReaderKeepingUpReadUntilClosed() = runTest {
        var dropped = 0L
        val full = Channel<ByteArray>(INBOX_CAPACITY, BufferOverflow.DROP_OLDEST) { dropped++ }
        repeat(INBOX_CAPACITY) { assertTrue(full.trySend(byteArrayOf(it.toByte())).isSuccess) }
        full.close()
        var fullRead = 0
        assertEquals(ReadEnd.Closed, full.readEach({ dropped }) { fullRead++; true })
        assertEquals(INBOX_CAPACITY, fullRead)

        var streamingDrops = 0L
        val stream = Channel<ByteArray>(INBOX_CAPACITY, BufferOverflow.DROP_OLDEST) { streamingDrops++ }
        var streamRead = 0
        val reader = async { stream.readEach({ streamingDrops }) { streamRead++; true } }
        repeat(8 * INBOX_CAPACITY) {
            stream.send(byteArrayOf(it.toByte()))
            yield()
        }
        stream.close()
        assertEquals(ReadEnd.Closed, reader.await())
        assertEquals(8 * INBOX_CAPACITY, streamRead)
    }

    @Test
    fun nullDropMetricsRefusedPacketsAndTransportSessionExtensionBehaveCleanly() = runTest {
        val plain = Channel<ByteArray>(INBOX_CAPACITY)
        repeat(4) { plain.send(ByteArray(1)) }
        plain.close()
        var plainRead = 0
        assertEquals(ReadEnd.Closed, plain.readEach({ null }) { plainRead++; true })
        assertEquals(4, plainRead)

        val refused = Channel<ByteArray>(INBOX_CAPACITY)
        repeat(3) { refused.send(ByteArray(1)) }
        var refusedRead = 0
        assertEquals(ReadEnd.Refused, refused.readEach({ 0L }) { ++refusedRead < 2 })
        assertEquals(2, refusedRead)

        val sessionChannel = Channel<ByteArray>(INBOX_CAPACITY)
        sessionChannel.send(byteArrayOf(7))
        sessionChannel.close()
        val session = FakeDropSession(sessionChannel, droppedInbound = 1L)
        assertEquals(ReadEnd.InboxFull, session.readEach { true })
    }

    private class FakeDropSession(
        private val channel: ReceiveChannel<ByteArray>,
        override val droppedInbound: Long,
    ) : TransportSession,
        InboundDropMetrics {
        override val id: SessionId = SessionId(1L)
        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
        override fun incoming(): ReceiveChannel<ByteArray> = channel
        override suspend fun close() = Unit
    }

    private companion object {
        const val INBOX_CAPACITY = 16
        const val PACKET_BYTES = 24
        const val HUGE_PACKET_BYTES = 60_000
    }
}
