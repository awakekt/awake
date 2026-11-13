/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.BatchedSession
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hand-off probe over the real [KtorTransportSession] send path with a counting socket: N clients,
 * M messages per tick. It prints the numbers (see them with `-i`); the assertions are structural
 * (frames), never timing, so the probe cannot flake.
 */
class BatchHandoffProbeTest {
    private class CountingSocket : WebSocketSession {
        val frames = AtomicLong()
        override val coroutineContext = SupervisorJob()
        override var masking = false
        override var maxFrameSize = Long.MAX_VALUE
        override val incoming = Channel<Frame>(Channel.UNLIMITED)
        override val outgoing = Channel<Frame>(Channel.UNLIMITED)
        override val extensions = emptyList<WebSocketExtension<*>>()
        override suspend fun send(frame: Frame) {
            if (frame is Frame.Binary) frames.incrementAndGet()
        }
        override suspend fun flush() = Unit

        @Deprecated("Required by WebSocketSession")
        override fun terminate() = Unit
    }

    private class Result(val frames: Long, val handoffNanos: Long, val handoffBytes: Long, val processCpuNanos: Long)

    private fun run(clients: Int, messages: Int, ticks: Int, batched: Boolean): Result {
        val sockets = List(clients) { CountingSocket() }
        val sessions = sockets.mapIndexed { i, socket ->
            val session = KtorTransportSession(
                id = SessionId(i + 1L),
                principal = null,
                socket = socket,
                inboxCapacity = 4,
                outboundCapacity = 4096,
                maxFrameSize = 64 * 1024,
                clientAcceptsBatches = batched,
            )
            BatchedSession(session, enabled = batched)
        }
        val packet = ByteArray(PACKET_BYTES)
        val thread = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        var nanos = 0L
        var bytes = 0L
        val cpu0 = os.processCpuTime
        runBlocking(Dispatchers.Default) {
            val id = Thread.currentThread().id
            repeat(ticks) {
                val t0 = System.nanoTime()
                val b0 = thread.getThreadAllocatedBytes(id)
                for (s in sessions) {
                    repeat(messages) { s.send(DeliveryChannel.Reliable, packet) }
                    s.flush()
                }
                nanos += System.nanoTime() - t0
                bytes += thread.getThreadAllocatedBytes(id) - b0
            }
            sessions.forEach { it.close() }
        }
        return Result(sockets.sumOf { it.frames.get() }, nanos, bytes, os.processCpuTime - cpu0)
    }

    private fun report(label: String, clients: Int, messages: Int, ticks: Int, r: Result) {
        val perClientTick = clients.toLong() * ticks
        println(
            "PROBE $label clients=$clients msgs/tick=$messages frames/client/tick=${r.frames.toDouble() / perClientTick} " +
                "handoff_us/client/tick=${r.handoffNanos / perClientTick / 1000.0} " +
                "alloc_B/client/tick=${r.handoffBytes / perClientTick} " +
                "process_cpu_ms=${r.processCpuNanos / 1_000_000}",
        )
    }

    @Test
    fun framesPerClientPerTickBeforeAndAfter() {
        val clients = 100
        val messages = 25
        val ticks = 200
        run(10, messages, 50, false) // warm up
        run(10, messages, 50, true)
        val before = run(clients, messages, ticks, batched = false)
        val after = run(clients, messages, ticks, batched = true)
        report("unbatched", clients, messages, ticks, before)
        report("batched  ", clients, messages, ticks, after)
        assertEquals(clients.toLong() * ticks * messages, before.frames)
        assertEquals(clients.toLong() * ticks, after.frames)
    }

    private companion object {
        const val PACKET_BYTES = 48
    }
}
