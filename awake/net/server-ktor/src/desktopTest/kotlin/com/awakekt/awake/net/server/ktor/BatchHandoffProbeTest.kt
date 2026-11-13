/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.ktor

import com.awakekt.awake.net.BatchedSession
import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.SessionId
import com.awakekt.awake.net.TransportSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private class Sink : TransportSession {
        var bytes = 0L
        override val id = SessionId(1)
        override suspend fun send(channel: DeliveryChannel, packet: ByteArray) {
            bytes += packet.size
        }
        override fun incoming(): ReceiveChannel<ByteArray> = Channel()
        override suspend fun close() = Unit
    }

    /** Thread-allocated bytes per client per tick of `produce`, after warm-up, on the calling thread. */
    private fun allocPerClientTick(clients: Int, ticks: Int, produce: suspend (Int) -> Unit): Long {
        val thread = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        return runBlocking {
            val id = Thread.currentThread().id
            repeat(WARMUP_TICKS) { for (c in 0 until clients) produce(c) }
            val before = thread.getThreadAllocatedBytes(id)
            repeat(ticks) { for (c in 0 until clients) produce(c) }
            (thread.getThreadAllocatedBytes(id) - before) / (clients.toLong() * ticks)
        }
    }

    @Test
    fun batchingAllocatesOnlyTheOneFramePerFlushAndPassThroughAllocatesWhatADirectSendDoes() {
        val clients = 100
        val messages = 25
        val packet = ByteArray(PACKET_BYTES)
        val sink = Sink()
        val batched = List(clients) { BatchedSession(sink, enabled = true) }
        val passThrough = List(clients) { BatchedSession(sink, enabled = false) }
        val frame = 2 + 1 + messages * (1 + PACKET_BYTES) + ARRAY_HEADER_BYTES
        val on = allocPerClientTick(clients, 400) { c ->
            val s = batched[c]
            repeat(messages) { s.send(DeliveryChannel.Reliable, packet) }
            s.sendLatest(7, packet)
            s.sendLatest(7, packet) // replaces the stale one in place
            s.flush()
        }
        val off = allocPerClientTick(clients, 400) { c ->
            repeat(messages) { passThrough[c].send(DeliveryChannel.Reliable, packet) }
        }
        val direct = allocPerClientTick(clients, 400) { _ ->
            repeat(messages) { sink.send(DeliveryChannel.Reliable, packet) }
        }
        println("PROBE alloc batched=$on B/client/tick (one frame is ${frame + 2 * (1 + PACKET_BYTES)} B) passThrough=$off direct=$direct")
        // `direct` is what the harness itself allocates. Batching adds the one exactly sized frame and at
        // most a flush continuation: no per-message garbage. Thread allocation is counted in TLAB
        // steps, so allow a few bytes of noise on the equality for the pass-through.
        val full = frame + 2 * (1 + PACKET_BYTES)
        assertTrue(on - direct <= full + CONTINUATION_SLACK_BYTES, "batched allocated $on B, harness $direct B")
        assertTrue(off - direct <= NOISE_BYTES, "pass-through allocated $off B, a direct send $direct B")
    }

    private companion object {
        const val WARMUP_TICKS = 300
        const val ARRAY_HEADER_BYTES = 16
        const val CONTINUATION_SLACK_BYTES = 96
        const val NOISE_BYTES = 32L
        const val PACKET_BYTES = 48
    }
}
