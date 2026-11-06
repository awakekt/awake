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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PolledStallWatchTest {
    /** A transport that can't say what it holds unsent, as the Ktor WebSocket client can't. */
    private class StreamTransport : Transport {
        override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Connected(SessionId(1L)))
        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) = Unit
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() = Unit
    }

    private fun TestScope.watch(): StallWatch {
        val ticker = CoroutineScope(StandardTestDispatcher(testScheduler))
        return StreamTransport().watchStalls(ticker, clockSeconds = { testScheduler.currentTime / MILLIS })
    }

    /** Hands the watch a packet every [everyMillis] for [forMillis] of virtual time. */
    private fun TestScope.stream(watch: StallWatch, forMillis: Long, everyMillis: Long = SNAPSHOT_MILLIS) {
        var left = forMillis
        while (left > 0L) {
            watch.received()
            testScheduler.advanceTimeBy(everyMillis)
            left -= everyMillis
        }
    }

    @Test
    fun rejectsNonPositiveCheckInterval() = runTest {
        assertFailsWith<IllegalArgumentException> { PolledStallWatch(this, checkMillis = 0) }
    }

    @Test
    fun aTransportThatIsNotDirectGetsThePolledWatch() = runTest {
        val watch = watch()
        assertIs<PolledStallWatch>(watch)
        watch.close()
    }

    @Test
    fun aStreamingPeerNeverStalls() = runTest {
        val watch = watch()
        watch.expectInbound = true
        stream(watch, forMillis = 10_000L)
        assertFalse(watch.state.value.stalled)
        watch.close()
    }

    @Test
    fun aSilentPeerStallsDownstreamAndNeverUpstream() = runTest {
        val watch = watch()
        watch.expectInbound = true
        stream(watch, forMillis = 3_000L)
        testScheduler.advanceTimeBy(1_500L)
        assertFalse(watch.state.value.stalled, "within two seconds of the last packet")
        testScheduler.advanceTimeBy(1_000L)
        assertTrue(watch.state.value.downstream)
        assertFalse(watch.state.value.upstream, "the socket can't say what it holds unsent")
        watch.close()
    }

    @Test
    fun theNextPacketEndsTheStallAndReportsItsLength() = runTest {
        val watch = watch()
        val ended = mutableListOf<Pair<StallDirection, Double>>()
        watch.onEnded = { direction, seconds -> ended += direction to seconds }
        watch.expectInbound = true
        stream(watch, forMillis = 1_000L)
        testScheduler.advanceTimeBy(4_000L)
        assertTrue(watch.state.value.downstream)
        watch.received()
        testScheduler.advanceTimeBy(TransportStallWatch.DEFAULT_CHECK_MILLIS.toLong() + 1L)
        assertEquals(TransportStallState.Clear, watch.state.value)
        assertEquals(StallDirection.Downstream, ended.single().first)
        assertTrue(ended.single().second > 4.0, "from the last packet before the silence to the next")
        watch.close()
    }

    @Test
    fun silenceIsNotAStallUntilInboundIsExpected() = runTest {
        val watch = watch()
        testScheduler.advanceTimeBy(10_000L)
        assertFalse(watch.state.value.stalled)
        watch.close()
    }

    @Test
    fun closeStopsTheTickerAndKeepsTheLastState() = runTest {
        val watch = watch()
        watch.expectInbound = true
        testScheduler.advanceTimeBy(3_000L)
        val last = watch.state.value
        assertTrue(last.downstream)
        watch.close()
        assertTrue(watch.isClosed)
        testScheduler.advanceTimeBy(5_000L)
        assertEquals(last, watch.state.value)
    }

    private companion object {
        const val MILLIS = 1000.0
        const val SNAPSHOT_MILLIS = 66L
    }
}
