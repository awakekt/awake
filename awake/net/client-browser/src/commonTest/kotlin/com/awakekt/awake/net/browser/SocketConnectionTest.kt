/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.browser

import com.awakekt.awake.net.ConnectionState
import com.awakekt.awake.net.DeliveryChannel
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The page's side of a browser socket (#1280): the socket's messages handed on in order with how
 * long they waited for the page, the worker's buffer reports read with the bytes still on their way
 * to it, and the close after everything that came before it.
 */
class SocketConnectionTest {
    @Test
    fun eachPacketIsHandedOnWithHowLongItWaitedForThePage() {
        val clock = Clock(now = 10_000.0)
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = clock::read)
        val seen = ArrayList<Pair<String, Double>>()
        socket.listen(onPacket = { seen += it.decodeToString() to socket.arrivalLag }, onClosed = {})
        // The reply reached the worker at 10 000 ms; a frame held the page until 10 250 ms.
        clock.now = 10_250.0
        socket.received("pong".encodeToByteArray(), arrivedAtMillis = 10_000.0)
        socket.received("snapshot".encodeToByteArray(), arrivedAtMillis = 10_240.0)
        assertEquals(listOf("pong" to 0.25, "snapshot" to 0.01), seen.map { (text, lag) -> text to millis(lag) })
        assertEquals(0.0, socket.arrivalLag, "no lag outside the hand-on")
    }

    @Test
    fun aStampAheadOfThePageClockIsNoLag() {
        val clock = Clock(now = 500.0)
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = clock::read)
        var lag = Double.NaN
        socket.listen(onPacket = { lag = socket.arrivalLag }, onClosed = {})
        socket.received(byteArrayOf(1), arrivedAtMillis = 500.4)
        assertEquals(0.0, lag)
    }

    @Test
    fun aPacketThePagesOwnSocketHandsOnWaitedForNothing() {
        val clock = Clock(now = 0.0)
        val socket = SocketConnection(FakePort(), runsInWorker = false, nowMillis = clock::read)
        var lag = Double.NaN
        socket.listen(onPacket = { lag = socket.arrivalLag }, onClosed = {})
        clock.now = 900.0
        socket.received(byteArrayOf(1))
        assertEquals(0.0, lag)
        assertFalse(socket.runsInWorker)
    }

    @Test
    fun packetsBeforeListenKeepTheirOrderAndArrivalTimes() {
        val clock = Clock(now = 0.0)
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = clock::read)
        socket.received("welcome".encodeToByteArray(), arrivedAtMillis = 0.0)
        socket.received("pong".encodeToByteArray(), arrivedAtMillis = 40.0)
        clock.now = 100.0
        val seen = ArrayList<Pair<String, Double>>()
        socket.listen(onPacket = { seen += it.decodeToString() to socket.arrivalLag }, onClosed = {})
        socket.received("later".encodeToByteArray(), arrivedAtMillis = 100.0)
        assertEquals(listOf("welcome" to 0.1, "pong" to 0.06, "later" to 0.0), seen.map { (text, lag) -> text to millis(lag) })
    }

    @Test
    fun aReaderOfIncomingGetsEveryPacketInOrder() {
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = Clock(0.0)::read)
        socket.received("one".encodeToByteArray(), arrivedAtMillis = 0.0)
        val incoming = socket.incoming()
        socket.received("two".encodeToByteArray(), arrivedAtMillis = 0.0)
        socket.closed(code = 1000, reason = "")
        assertEquals(listOf("one", "two"), generateSequence { incoming.tryReceive().getOrNull()?.decodeToString() }.toList())
        assertTrue(incoming.tryReceive().isClosed, "the channel closes with the socket")
    }

    @Test
    fun eachSendIsPostedAtOnceWhole() {
        val port = FakePort()
        val socket = SocketConnection(port, runsInWorker = true, nowMillis = Clock(0.0)::read)
        socket.sendNow("ping".encodeToByteArray())
        runAtOnce { socket.send(DeliveryChannel.Unreliable, "chat".encodeToByteArray()) }
        assertEquals(listOf("ping", "chat"), port.sent.map { it.decodeToString() })
    }

    @Test
    fun bufferedBytesCountWhatTheWorkerHasNotTakenYet() {
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = Clock(0.0)::read)
        assertEquals(0, socket.bufferedBytes)
        socket.sendNow(ByteArray(300))
        socket.sendNow(ByteArray(100))
        assertEquals(400, socket.bufferedBytes, "posted, not yet taken by the worker")
        // The worker sent the first: the socket holds 280 of its 300 bytes.
        socket.buffered(bufferedAmount = 280, takenBytes = 300)
        assertEquals(380, socket.bufferedBytes)
        socket.buffered(bufferedAmount = 0, takenBytes = 400)
        assertEquals(0, socket.bufferedBytes)
    }

    @Test
    fun aSendHeldBackOnTheWorkersReportGoesOnceItDrains() {
        val port = FakePort()
        val socket = SocketConnection(port, runsInWorker = true, nowMillis = Clock(0.0)::read)
        socket.buffered(bufferedAmount = 600, takenBytes = 0)
        fun trySend() {
            if (socket.bufferedBytes > LIMIT) socket.schedule(RETRY_MILLIS) { trySend() } else socket.sendNow(ByteArray(8))
        }
        trySend()
        assertEquals(0, port.sent.size, "the socket holds more than the limit, so the send waits")
        assertEquals(listOf(RETRY_MILLIS), port.scheduledMillis)
        socket.buffered(bufferedAmount = 0, takenBytes = 0)
        port.runScheduled()
        assertEquals(listOf(8), port.sent.map { it.size })
    }

    @Test
    fun onThePageBufferedBytesIsTheSocketsOwnReading() {
        val port = FakePort()
        val socket = SocketConnection(port, runsInWorker = false, nowMillis = Clock(0.0)::read)
        socket.sendNow(ByteArray(300))
        port.bufferedAmount = 120
        assertEquals(120, socket.bufferedBytes, "read from the socket, not counted from the posts")
        socket.closed(code = 1000, reason = "")
        assertEquals(0, socket.bufferedBytes, "nothing is buffered on a closed socket")
    }

    @Test
    fun theCloseComesAfterEverythingBeforeIt() {
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = Clock(0.0)::read)
        val events = ArrayList<String>()
        assertNull(socket.closeCode)
        socket.listen(onPacket = { events += it.decodeToString() }, onClosed = { events += "closed" })
        socket.received("kick".encodeToByteArray(), arrivedAtMillis = 0.0)
        socket.closed(code = 1008, reason = "policy")
        assertEquals(listOf("kick", "closed"), events)
        assertEquals(ConnectionState.Disconnected, socket.state.value)
        assertEquals(1008, socket.closeCode)
        assertEquals("policy", socket.closeReason)
        socket.closed(code = 1000, reason = "")
        assertEquals(listOf("kick", "closed"), events, "the close is told once")
        assertEquals(1008, socket.closeCode, "the first close report stands")
    }

    @Test
    fun aCloseBeforeListenIsToldOnListen() {
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = Clock(0.0)::read)
        socket.received("kick".encodeToByteArray(), arrivedAtMillis = 0.0)
        socket.closed(code = 1000, reason = "")
        val events = ArrayList<String>()
        socket.listen(onPacket = { events += it.decodeToString() }, onClosed = { events += "closed" })
        assertEquals(listOf("kick", "closed"), events)
    }

    @Test
    fun closingAsksTheSocketAndDropsLaterSendsAndPackets() {
        val port = FakePort()
        val socket = SocketConnection(port, runsInWorker = true, nowMillis = Clock(0.0)::read)
        socket.sendNow(ByteArray(50))
        var closed = 0
        val seen = ArrayList<ByteArray>()
        socket.listen(onPacket = { seen += it }, onClosed = { closed++ })
        runAtOnce { socket.close() }
        assertTrue(port.closeAsked)
        assertEquals(1, closed)
        assertEquals(0, socket.bufferedBytes, "nothing is buffered on a closed socket")
        socket.sendNow(ByteArray(10))
        assertEquals(1, port.sent.size, "a send after the close is dropped")
        socket.received(byteArrayOf(7), arrivedAtMillis = 0.0)
        assertTrue(seen.isEmpty(), "a packet after the close is not handed on")
        // The close event the socket then reports still records its code.
        socket.closed(code = 1000, reason = "bye")
        assertEquals(1000, socket.closeCode)
        assertEquals(1, closed)
    }

    @Test
    fun packetsReachTheReaderByteForByte() {
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = Clock(0.0)::read)
        val every = ByteArray(256) { it.toByte() }
        var seen: ByteArray? = null
        socket.listen(onPacket = { seen = it }, onClosed = {})
        socket.received(every, arrivedAtMillis = 0.0)
        assertContentEquals(every, seen)
    }

    @Test
    fun acquiresWakeLockOnOpenAndReleasesIdempotentlyOnClose() {
        val wakeLocks = FakeWakeLock()
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = Clock(0.0)::read, acquireWakeLock = wakeLocks::acquire)
        assertEquals(1, wakeLocks.acquired)
        assertEquals(0, wakeLocks.released)
        runAtOnce { socket.close() }
        assertEquals(1, wakeLocks.released)
        runAtOnce { socket.close() }
        socket.closed(code = 1000, reason = "")
        assertEquals(1, wakeLocks.released, "the wake lock is released once")
    }

    @Test
    fun releasesWakeLockIdempotentlyOnRemoteClose() {
        val wakeLocks = FakeWakeLock()
        val socket = SocketConnection(FakePort(), runsInWorker = false, nowMillis = Clock(0.0)::read, acquireWakeLock = wakeLocks::acquire)
        assertEquals(1, wakeLocks.acquired)
        socket.closed(code = 1006, reason = "dropped")
        assertEquals(1, wakeLocks.released)
        socket.closed(code = 1000, reason = "")
        runAtOnce { socket.close() }
        assertEquals(1, wakeLocks.released)
    }

    @Test
    fun releasesWakeLockEvenWhenClosedListenerThrows() {
        val wakeLocks = FakeWakeLock()
        val socket = SocketConnection(FakePort(), runsInWorker = true, nowMillis = Clock(0.0)::read, acquireWakeLock = wakeLocks::acquire)
        socket.listen(onPacket = {}, onClosed = { error("listener failure") })
        assertFailsWith<IllegalStateException> { socket.closed(code = 1006, reason = "") }
        assertEquals(1, wakeLocks.released)
    }

    @Test
    fun skipsWakeLockWhenHoldWakeLockIsFalse() {
        val wakeLocks = FakeWakeLock()
        val socket = SocketConnection(
            FakePort(),
            runsInWorker = true,
            nowMillis = Clock(0.0)::read,
            holdWakeLock = false,
            acquireWakeLock = wakeLocks::acquire,
        )
        assertEquals(0, wakeLocks.acquired)
        runAtOnce { socket.close() }
        socket.closed(code = 1000, reason = "")
        assertEquals(0, wakeLocks.acquired)
        assertEquals(0, wakeLocks.released)
    }

    @Test
    fun scheduleAfterCloseIsIgnored() {
        val port = FakePort()
        val socket = SocketConnection(port, runsInWorker = true, nowMillis = Clock(0.0)::read)
        socket.closed(code = 1000, reason = "")
        socket.schedule(RETRY_MILLIS) {}
        assertTrue(port.scheduledMillis.isEmpty())
    }

    private class FakeWakeLock {
        var acquired = 0
        var released = 0

        fun acquire(): WakeLock {
            acquired++
            return WakeLock { released++ }
        }
    }

    private class Clock(var now: Double) {
        fun read(): Double = now
    }

    private class FakePort : SocketPort {
        val sent = ArrayList<ByteArray>()
        var closeAsked = false
        var bufferedAmount = 0L
        val scheduledMillis = ArrayList<Int>()
        private val scheduled = ArrayList<() -> Unit>()

        override fun send(packet: ByteArray) {
            sent += packet
        }

        override fun close() {
            closeAsked = true
        }

        override fun schedule(millis: Int, action: () -> Unit) {
            scheduledMillis += millis
            scheduled += action
        }

        override fun bufferedAmount(): Long = bufferedAmount

        fun runScheduled() {
            val due = scheduled.toList()
            scheduled.clear()
            due.forEach { it() }
        }
    }

    /** Runs [block], which never suspends, to its end now. */
    private fun runAtOnce(block: suspend () -> Unit) {
        var done = false
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) {
                it.getOrThrow()
                done = true
            },
        )
        assertTrue(done)
    }

    /** [seconds] to the nearest millisecond, still in seconds. */
    private fun millis(seconds: Double): Double = round(seconds * MILLIS) / MILLIS

    private companion object {
        const val LIMIT = 512L
        const val RETRY_MILLIS = 16
        const val MILLIS = 1000.0
    }
}
