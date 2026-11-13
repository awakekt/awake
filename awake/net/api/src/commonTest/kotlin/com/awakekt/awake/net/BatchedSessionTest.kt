/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatchedSessionTest {
    private class Recorder : TransportSession {
        val frames = mutableListOf<Pair<DeliveryChannel, ByteArray>>()
        override val id = SessionId(1)
        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
            frames += channel to packet
        }
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() = Unit
    }

    private fun bytes(text: String) = text.encodeToByteArray()

    private fun Recorder.texts(): List<List<String>> =
        frames.map { (_, frame) -> BatchFrame.decode(frame)!!.map { it.decodeToString() } }

    @Test
    fun mixedBatchRoundTripsInOrderAsOneFrame() = runTest {
        val sink = Recorder()
        val session = BatchedSession(sink, enabled = true)
        session.send(DeliveryChannel.Reliable, bytes("chat"))
        session.sendLatest(0, bytes("snapshot-1"))
        session.send(DeliveryChannel.Reliable, bytes("event"))
        session.send(DeliveryChannel.Unreliable, ByteArray(0))
        session.flush()
        assertEquals(listOf(listOf("chat", "snapshot-1", "event", "")), sink.texts())
        assertEquals(DeliveryChannel.Reliable, sink.frames.single().first)
        assertEquals(1L, session.framesWritten)
        assertEquals(0, session.staged)
    }

    @Test
    fun staleSnapshotIsDroppedNotQueuedAndTheNewestKeepsItsPlace() = runTest {
        val sink = Recorder()
        val session = BatchedSession(sink, enabled = true)
        session.sendLatest(7, bytes("snap-old"))
        session.send(DeliveryChannel.Reliable, bytes("a"))
        session.sendLatest(7, bytes("snap-new"))
        session.sendLatest(8, bytes("other-lane"))
        session.send(DeliveryChannel.Reliable, bytes("b"))
        session.flush()
        assertEquals(listOf(listOf("a", "snap-new", "other-lane", "b")), sink.texts())
        assertEquals(1L, session.staleDropped)
    }

    @Test
    fun replacingAStalePacketOfAnotherSizeKeepsEveryOtherEntryIntact() = runTest {
        val sink = Recorder()
        val session = BatchedSession(sink, enabled = true)
        session.send(DeliveryChannel.Reliable, bytes("first"))
        session.sendLatest(1, bytes("a-much-longer-stale-snapshot"))
        session.send(DeliveryChannel.Reliable, bytes("middle"))
        session.sendLatest(2, bytes("x"))
        session.sendLatest(1, bytes("new"))
        session.flush()
        assertEquals(listOf(listOf("first", "middle", "x", "new")), sink.texts())
        // The staging buffer is reused: a second tick starts clean.
        session.send(DeliveryChannel.Reliable, bytes("next"))
        session.flush()
        assertEquals(listOf("next"), sink.texts().last())
    }

    @Test
    fun rangeOverloadsStageASliceOfAReusedArray() = runTest {
        val sink = Recorder()
        val session = BatchedSession(sink, enabled = true)
        val scratch = bytes("..hello..world")
        session.send(DeliveryChannel.Reliable, scratch, 2, 5)
        session.sendLatest(0, scratch, 9, 5)
        scratch.fill('#'.code.toByte()) // the caller may reuse its array at once
        session.flush()
        assertEquals(listOf(listOf("hello", "world")), sink.texts())
        assertFailsWith<IllegalArgumentException> { session.send(DeliveryChannel.Reliable, scratch, 10, 10) }
    }

    @Test
    fun aTickOverTheCapSplitsIntoOrderedBatchesEachWithinTheCap() = runTest {
        val sink = Recorder()
        val session = BatchedSession(sink, maxBatchBytes = 64, enabled = true)
        val sent = (0 until 40).map { "message-$it" }
        sent.forEach { session.send(DeliveryChannel.Reliable, bytes(it)) }
        session.sendLatest(0, ByteArray(200)) // larger than the cap: alone in its own batch
        session.flush()
        assertTrue(sink.frames.size > 2)
        assertTrue(sink.frames.dropLast(1).all { it.second.size <= 64 })
        assertEquals(sent, sink.texts().flatten().take(sent.size))
        assertEquals(200, BatchFrame.decode(sink.frames.last().second)!!.single().size)
        assertEquals(sink.frames.size.toLong(), session.framesWritten)
    }

    @Test
    fun unbatchedSessionPassesEverythingStraightThroughAndFlushIsANoOp() = runTest {
        val sink = Recorder()
        val session = BatchedSession(sink) // the recorder did not negotiate batching
        assertTrue(!session.enabled)
        session.send(DeliveryChannel.Reliable, bytes("plain"))
        session.sendLatest(0, bytes("s1"))
        session.sendLatest(0, bytes("s2"))
        session.flush()
        assertEquals(listOf("plain", "s1", "s2"), sink.frames.map { it.second.decodeToString() })
        assertEquals(0L, session.staleDropped)
    }

    @Test
    fun emptyFlushWritesNothingAndMinimumCapIsEnforced() = runTest {
        val sink = Recorder()
        BatchedSession(sink, enabled = true).flush()
        assertTrue(sink.frames.isEmpty())
        assertFailsWith<IllegalArgumentException> { BatchedSession(sink, maxBatchBytes = 4) }
    }

    @Test
    fun malformedBatchesAreRejectedWholeNeverHalfDelivered() {
        val good = BatchFrame.encode(listOf(bytes("one"), bytes("two")))
        assertEquals(2, BatchFrame.decode(good)!!.size)
        assertNull(BatchFrame.decode(ByteArray(0)))
        assertNull(BatchFrame.decode(bytes("plain game packet")))
        assertNull(BatchFrame.decode(good.copyOf().also { it[1] = 9 })) // unknown version
        assertNull(BatchFrame.decode(good.copyOf(good.size - 1))) // cut short
        assertNull(BatchFrame.decode(good + byteArrayOf(0))) // trailing bytes
        assertNull(BatchFrame.decode(byteArrayOf(0xB7.toByte(), 1, 0x7F))) // count beyond the frame
        assertNull(BatchFrame.decode(byteArrayOf(0xB7.toByte(), 1, 1, -1, -1, -1, -1, -1, -1))) // endless varint
    }

    @Test
    fun unbatchingTransportDeliversTheSameStreamAndSurvivesAMalformedFrame() = runTest {
        val frames = Channel<ByteArray>(Channel.UNLIMITED)
        val wire = object : Transport {
            override val state = kotlinx.coroutines.flow.MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
            override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
            override fun incoming(): ReceiveChannel<ByteArray> = frames
            override suspend fun close() = Unit
        }
        val client = UnbatchingTransport(wire, this)
        frames.send(BatchFrame.encode(listOf(bytes("a"), bytes("b"))))
        frames.send(bytes("garbage")) // dropped, connection stays up
        frames.send(BatchFrame.encode(listOf(bytes("c"))))
        frames.close()
        val received = buildList { for (p in client.incoming()) add(p.decodeToString()) }
        assertEquals(listOf("a", "b", "c"), received)
        assertEquals(1L, client.malformed)
    }
}
