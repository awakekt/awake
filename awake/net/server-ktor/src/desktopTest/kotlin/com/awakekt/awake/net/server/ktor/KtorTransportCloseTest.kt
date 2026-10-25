/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class KtorTransportCloseTest {
    private class RecordingLogger : Logger by KtorSimpleLogger("transport-close-test") {
        val failures = mutableListOf<Throwable>()
        override fun warn(message: String?, cause: Throwable?) {
            if (cause != null) failures += cause
        }
    }

    private class Socket : WebSocketSession {
        override val coroutineContext = SupervisorJob()
        override var masking = false
        override var maxFrameSize = Long.MAX_VALUE
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        override val outgoing = Channel<Frame>(Channel.UNLIMITED)
        override val extensions = emptyList<WebSocketExtension<*>>()
        val events = mutableListOf<String>()
        var blockedSend: CompletableDeferred<Unit>? = null
        var blockedFlush: CompletableDeferred<Unit>? = null
        var failSend = false

        override suspend fun send(frame: Frame) {
            when (frame) {
                is Frame.Binary -> {
                    blockedSend?.await()
                    if (failSend) throw IOException("Synthetic write failure")
                    events += frame.readBytes().decodeToString()
                }
                is Frame.Close -> events += "close:${frame.readReason()?.code}"
                else -> error("Unexpected frame")
            }
        }

        override suspend fun flush() {
            blockedFlush?.await()
            events += "flush"
        }

        @Deprecated("Required by WebSocketSession")
        override fun terminate() = Unit
    }

    private fun TestScope.session(socket: Socket, capacity: Int = 32, logger: Logger = RecordingLogger()) = KtorTransportSession(
        id = SessionId(1),
        principal = null,
        socket = socket,
        inboxCapacity = 2,
        outboundCapacity = capacity,
        maxFrameSize = 1024,
        writerDispatcher = StandardTestDispatcher(testScheduler),
        closeTimeout = 100.milliseconds,
        logger = logger,
    )

    @Test
    fun normalCloseDrainsAnIdleWritersQueueBeforeCloseAndFlush() = runTest {
        val socket = Socket()
        val session = session(socket)
        repeat(32) { session.send(DeliveryChannel.Reliable, "packet:$it".encodeToByteArray()) }
        // The StandardTestDispatcher has not let the writer run before close.
        assertTrue(socket.events.isEmpty())
        session.close()
        assertEquals((0 until 32).map { "packet:$it" } + listOf("close:1000", "flush"), socket.events)
        repeat(3) { session.close() }
        session.send(DeliveryChannel.Reliable, "after-close".encodeToByteArray())
        assertEquals(34, socket.events.size)
    }

    @Test
    fun concurrentCloseWaitsForOneDrainEvenIfTheFirstCallerIsCancelled() = runTest {
        val socket = Socket().apply { blockedSend = CompletableDeferred() }
        val session = session(socket)
        session.send(DeliveryChannel.Reliable, "final".encodeToByteArray())
        val first = launch { session.close() }
        val second = launch { session.close() }
        runCurrent()
        first.cancel()
        assertFalse(second.isCompleted)
        socket.blockedSend!!.complete(Unit)
        runCurrent()
        first.join()
        second.join()
        assertEquals(listOf("final", "close:1000", "flush"), socket.events)
    }

    @Test
    fun blockedWriterOrFlushTimesOutAndCancelsTheSocket() = runTest {
        for (blockSend in listOf(true, false)) {
            val socket = Socket().apply {
                if (blockSend) blockedSend = CompletableDeferred() else blockedFlush = CompletableDeferred()
            }
            val session = session(socket)
            session.send(DeliveryChannel.Reliable, "final".encodeToByteArray())
            val close = launch { session.close() }
            runCurrent()
            assertFalse(close.isCompleted)
            advanceTimeBy(100)
            runCurrent()
            assertTrue(close.isCompleted)
            assertFalse(socket.isActive)
            session.close()
        }
    }

    @Test
    fun writerFailureStillClosesAndReleasesRepeatedClose() = runTest {
        val socket = Socket().apply { failSend = true }
        val logger = RecordingLogger()
        val session = session(socket, logger = logger)
        session.send(DeliveryChannel.Reliable, "final".encodeToByteArray())
        runCurrent()
        session.close()
        session.close()
        assertEquals(listOf("close:1000", "flush"), socket.events)
        assertTrue(session.incoming().receiveCatching().isClosed)
        assertEquals(1, logger.failures.size)
        assertEquals("Synthetic write failure", logger.failures.single().message)
    }

    @Test
    fun outboundOverflowAbortsInsteadOfPromisingADrain() = runTest {
        val socket = Socket()
        val session = session(socket, capacity = 1)
        session.send(DeliveryChannel.Reliable, "queued".encodeToByteArray())
        session.send(DeliveryChannel.Reliable, "overflow".encodeToByteArray())
        session.close()
        assertEquals(listOf("close:${CloseReason.Codes.TRY_AGAIN_LATER.code}", "flush"), socket.events)
    }

    @Test
    fun sendOnOverflowWaitsOnlyUntilTheCloseDeadline() = runTest {
        val socket = Socket().apply { blockedFlush = CompletableDeferred() }
        val logger = RecordingLogger()
        val session = session(socket, capacity = 1, logger = logger)
        session.send(DeliveryChannel.Reliable, "queued".encodeToByteArray())
        val overflowingSend = launch(start = CoroutineStart.UNDISPATCHED) {
            session.send(DeliveryChannel.Reliable, "overflow".encodeToByteArray())
        }
        runCurrent()
        assertFalse(overflowingSend.isCompleted)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(overflowingSend.isCompleted)
        assertFalse(socket.isActive)
        session.close()
        assertTrue(logger.failures.isEmpty())
    }
}
