/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportSession
import com.awakekt.awake.net.droppedInbound
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KtorInboundDropTest {
    private class Socket : WebSocketSession {
        override val coroutineContext = SupervisorJob()
        override var masking = false
        override var maxFrameSize = Long.MAX_VALUE
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        override val outgoing = Channel<Frame>(Channel.UNLIMITED)
        override val extensions = emptyList<WebSocketExtension<*>>()
        override suspend fun flush() = Unit

        @Deprecated("Required by WebSocketSession")
        override fun terminate() = Unit
    }

    private fun TestScope.session(socket: Socket, capacity: Int = 2) = KtorTransportSession(
        id = SessionId(1),
        principal = null,
        socket = socket,
        inboxCapacity = capacity,
        outboundCapacity = 2,
        maxFrameSize = 1024,
        writerDispatcher = StandardTestDispatcher(testScheduler),
    )

    private suspend fun Socket.packet(value: Int) = incoming.send(Frame.Binary(true, value.toString().encodeToByteArray()))

    @Test
    fun overflowCountsExactlyTheEvictedPacketsAndKeepsNewestInOrder() = runTest {
        val socket = Socket()
        val session = session(socket)
        val port: TransportSession = session
        val pump = launch { session.pump() }
        repeat(5) { socket.packet(it) }
        runCurrent()
        assertEquals(3L, port.droppedInbound)
        assertEquals("3", session.incoming().receive().decodeToString())
        assertEquals("4", session.incoming().receive().decodeToString())
        assertEquals(3L, port.droppedInbound)
        socket.incoming.close()
        pump.join()
        assertEquals(3L, port.droppedInbound)
    }

    @Test
    fun burstsThatFitAndUnreadPacketsOnGracefulCloseDoNotCountAsLoss() = runTest {
        val socket = Socket()
        val session = session(socket)
        val pump = launch { session.pump() }
        repeat(2) { socket.packet(it) }
        runCurrent()
        assertEquals(0L, session.droppedInbound)
        repeat(2) { assertEquals(it.toString(), session.incoming().receive().decodeToString()) }
        repeat(2) { socket.packet(it + 2) }
        runCurrent()
        socket.incoming.close()
        pump.join()
        assertEquals(0L, session.droppedInbound)
        assertEquals("2", session.incoming().receive().decodeToString())
        assertEquals("3", session.incoming().receive().decodeToString())
    }

    @Test
    fun concurrentReaderAccountsForEveryPacketWithoutCapacityGuessing() = runTest {
        val socket = Socket()
        val session = session(socket, capacity = 8)
        val received = ConcurrentLinkedQueue<Int>()
        val reader = launch(Dispatchers.Default) {
            for (packet in session.incoming()) received += packet.decodeToString().toInt()
        }
        val pump = launch { session.pump() }
        repeat(1_000) { socket.packet(it) }
        socket.incoming.close()
        pump.join()
        reader.join()
        assertEquals(1_000L, received.size.toLong() + session.droppedInbound)
        assertTrue(received.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun cancelledReceiveHandoffIsAnActualDiscardRatherThanAnOverflowClaim() = runTest {
        val socket = Socket()
        val session = session(socket)
        val reader = launch(start = CoroutineStart.UNDISPATCHED) { session.incoming().receive() }
        socket.packet(1)
        // Deliver while the receiver's continuation is still waiting on the test dispatcher.
        val pump = launch(start = CoroutineStart.UNDISPATCHED) { session.pump() }
        reader.cancel()
        runCurrent()
        assertEquals(1L, session.droppedInbound)
        socket.incoming.close()
        pump.join()
    }
}
