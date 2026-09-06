/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import com.awakekt.awake.net.DeliveryChannel
import com.awakekt.awake.net.NetId
import com.awakekt.awake.net.PacketReader
import com.awakekt.awake.net.PacketWriter
import kotlin.time.TimeSource

/**
 * Thin client over [KtorWebSocketClient]: sends intents, decodes snapshots into a reusable
 * [SnapshotBuffer]. It holds no authoritative state and does no prediction -- prediction and
 * reconciliation are Phase 3, and adding them before snapshots are proven correct hides
 * replication bugs behind smoothing.
 */
class NetDemoClient(
    /** Internal so a test can send a packet this client would never construct. */
    internal val transport: KtorWebSocketClient,
) {
    /** Latest server state. Overwritten in place; never copied per tick. */
    val snapshot: SnapshotBuffer = SnapshotBuffer()

    var netId: NetId? = null
        private set

    var malformedPackets: Int = 0
        private set

    private val reader = PacketReader()
    private val writer = PacketWriter()
    private val interpolator = SnapshotInterpolator()

    // Monotonic, because interpolation reasons about elapsed time and a wall clock can jump
    // backwards. TimeSource.Monotonic works on every target, so this stays common.
    private val started = TimeSource.Monotonic.markNow()

    suspend fun connect(displayName: String) {
        transport.connect()
        transport.send(DeliveryChannel.Reliable, ClientFrame.Join(displayName).encode())
    }

    suspend fun sendInput(tick: Long, moveX: Float, moveY: Float) {
        TickCodec.encodeInput(writer, netId?.value ?: 0L, tick, moveX, moveY)
        transport.send(DeliveryChannel.Unreliable, writer.toPacket())
    }

    /** Consumes inbound packets until [predicate] holds against the updated client state. */
    suspend fun receiveUntil(predicate: (NetDemoClient) -> Boolean) = consume(onUpdate = {}, stopWhen = predicate)

    /** Consumes until the connection closes, calling [onUpdate] after every applied packet. */
    suspend fun receiveLoop(onUpdate: (NetDemoClient) -> Unit) = consume(onUpdate) { false }

    /**
     * What to draw: remote entities smoothed between the two most recent snapshots. Snapshots
     * arrive 20x per second while frames render far more often, so drawing [snapshot] directly
     * shows a visible step on every one.
     */
    fun interpolated(): SnapshotBuffer = interpolator.sample(elapsedSeconds())

    private fun elapsedSeconds(): Float = started.elapsedNow().inWholeMicroseconds / MICROS_PER_SECOND

    /** Index into [snapshot], or -1 when the server has not sent this entity yet. */
    fun indexOf(id: NetId): Int = snapshot.indexOf(id.value)

    private suspend fun consume(onUpdate: (NetDemoClient) -> Unit, stopWhen: (NetDemoClient) -> Boolean) {
        for (packet in transport.incoming()) {
            if (!apply(packet)) continue
            onUpdate(this)
            if (stopWhen(this)) return
        }
    }

    private fun apply(packet: ByteArray): Boolean = when (opcodeOf(packet)) {
        TickCodec.Opcode.SNAPSHOT -> {
            reader.reset(packet)
            val decoded = TickCodec.decodeSnapshot(reader, snapshot)
            if (decoded) interpolator.accept(snapshot, elapsedSeconds()) else malformedPackets += 1
            decoded
        }

        TickCodec.Opcode.JSON -> when (val frame = decodeServerFrame(packet)) {
            is ServerFrame.Welcome -> true.also { netId = NetId(frame.netId) }
            is ServerFrame.Rejected -> true
            null -> false.also { malformedPackets += 1 }
        }

        else -> false.also { malformedPackets += 1 }
    }

    suspend fun close() {
        transport.close()
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000f
    }
}
