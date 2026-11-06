/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransportStallWatchTest {
    /** A socket whose buffer, timers and clock the test moves by hand. */
    private class StalledSocket : DirectTransport {
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Connected(SessionId(1L)))
        override var bufferedBytes: Long = 0L
        val timers = ArrayDeque<Pair<Int, () -> Unit>>()
        var now = 0.0

        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() {
            state.value = ConnectionState.Disconnected
        }
        override fun sendNow(packet: ByteArray) {
            bufferedBytes += packet.size
        }
        override fun listen(onPacket: (ByteArray) -> Unit, onClosed: () -> Unit) = Unit
        override fun schedule(millis: Int, action: () -> Unit) {
            timers.addLast(millis to action)
        }

        /** Runs due timers for [seconds], advancing the clock by each timer's delay. */
        fun run(seconds: Double, onTick: () -> Unit = {}) {
            val until = now + seconds
            while (now < until - 1e-9) {
                val (millis, action) = timers.removeFirstOrNull() ?: return
                now += millis / 1000.0
                onTick()
                action()
            }
        }
    }

    private val socket = StalledSocket()

    private fun watch(): TransportStallWatch =
        TransportStallWatch(socket, clockSeconds = { socket.now })

    @Test
    fun rejectsNonPositiveCheckInterval() {
        assertFailsWith<IllegalArgumentException> { TransportStallWatch(socket, checkMillis = 0) }
    }

    @Test
    fun schedulesOneCheckOnTheSocketThread() {
        watch()
        assertEquals(listOf(TransportStallWatch.DEFAULT_CHECK_MILLIS), socket.timers.map { it.first })
    }

    @Test
    fun aSocketThatStopsDrainingIsReportedUpstreamWithoutAnySendsToDriveIt() {
        val watch = watch()
        socket.sendNow(ByteArray(520))
        socket.run(1.0)
        assertFalse(watch.state.value.stalled)
        socket.run(0.5)
        assertTrue(watch.state.value.upstream)
        assertEquals(1.25, watch.state.value.upstreamSeconds, 1e-9, "the wait starts at the first check")
    }

    @Test
    fun theStallEndsAndIsReportedOnceTheBufferDrains() {
        val watch = watch()
        val ended = mutableListOf<Pair<StallDirection, Double>>()
        watch.onEnded = { direction, seconds -> ended += direction to seconds }
        socket.sendNow(ByteArray(520))
        socket.run(3.0)
        assertTrue(watch.state.value.upstream)
        socket.bufferedBytes = 0L
        socket.run(0.25)
        assertEquals(TransportStallState.Clear, watch.state.value)
        assertEquals(StallDirection.Upstream, ended.single().first)
        assertEquals(3.0, ended.single().second, 1e-9)
    }

    @Test
    fun aSilentPeerIsReportedDownstreamAndAPacketClearsItAtOnce() {
        val watch = watch()
        watch.expectInbound = true
        watch.received()
        socket.run(2.0) { if (socket.now < 1.0) watch.received() }
        assertFalse(watch.state.value.stalled)
        socket.run(1.5)
        assertTrue(watch.state.value.downstream)
        watch.received()
        assertEquals(TransportStallState.Clear, watch.state.value)
    }

    @Test
    fun aStreamingPeerNeverStalls() {
        val watch = watch()
        watch.expectInbound = true
        socket.run(10.0) { watch.received() }
        assertFalse(watch.state.value.stalled)
    }

    @Test
    fun stopsCheckingOnceTheTransportDisconnects() {
        val watch = watch()
        socket.state.value = ConnectionState.Disconnected
        socket.run(0.25)
        assertTrue(watch.isClosed)
        assertTrue(socket.timers.isEmpty())
    }

    @Test
    fun closeStopsChecksAndKeepsTheLastState() {
        val watch = watch()
        socket.sendNow(ByteArray(520))
        socket.run(1.5)
        val last = watch.state.value
        watch.close()
        socket.run(1.0)
        assertTrue(socket.timers.isEmpty())
        assertEquals(last, watch.state.value)
    }

    @Test
    fun aDirectTransportGetsTheWatchOnItsOwnThread() {
        val watch = (socket as Transport).watchStalls(CoroutineScope(EmptyCoroutineContext), clockSeconds = { socket.now })
        assertIs<TransportStallWatch>(watch)
        socket.sendNow(ByteArray(520))
        socket.run(1.5)
        assertTrue(watch.state.value.upstream, "a direct transport is watched upstream too")
    }

    @Test
    fun watchStallsAppliesTheGivenThresholds() {
        val watch = socket.watchStalls(checkMillis = 100, upstreamAfterSeconds = 0.5, downstreamAfterSeconds = 3.0)
        assertEquals(100, watch.checkMillis)
        assertEquals(0.5, watch.detector.upstreamAfterSeconds)
        assertEquals(3.0, watch.detector.downstreamAfterSeconds)
    }
}
