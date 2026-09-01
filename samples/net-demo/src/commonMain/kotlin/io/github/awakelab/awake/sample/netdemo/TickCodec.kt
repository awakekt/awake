/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import io.github.awakelab.awake.net.PacketReader
import io.github.awakelab.awake.net.PacketWriter
import kotlin.math.roundToInt

/**
 * Binary codec for the per-tick frames, built on `:awake:net:api`'s packet buffers. The
 * opcodes and the snapshot's shape live here rather than in the engine module because a wire
 * protocol is game policy (docs/reference/framework-game-boundary.md).
 *
 * The handshake stays JSON (see Protocol.kt) -- only input and snapshot are hot enough to earn
 * a hand-rolled format.
 *
 * Three properties are the whole reason this exists, and no general-purpose format gives them:
 * quantization (positions to centimetres, movement to 1/32767 of a unit), varints so a small
 * entity count costs one byte, and a decode path that allocates nothing.
 *
 * Not here yet: delta against a last-acked baseline. That needs ack plumbing, and delta
 * against *last sent* would be silently correct on WebSocket and silently wrong the moment
 * the UDP transport lands. [SnapshotBuffer] is already the shape a baseline would diff
 * against, so adding it later does not reshape the wire.
 */
object TickCodec {
    /** First byte of every packet. Frames not listed here are JSON, tagged by [Opcode.Json]. */
    object Opcode {
        const val JSON: Byte = 0x01
        const val INPUT: Byte = 0x02
        const val SNAPSHOT: Byte = 0x03
    }

    /**
     * Bound on entities per snapshot, checked before any buffer grows. Without it, a forged
     * count makes the decoder allocate whatever the attacker asks for -- the classic
     * length-prefix attack, and the reason decode bounds are a security control and not a
     * tuning knob.
     */
    const val MAX_PLAYERS_PER_SNAPSHOT: Int = 4_096

    private const val POSITION_SCALE = 100f // centimetres
    private const val MOVE_SCALE = 32_767f

    /**
     * [netId] is the entity the input addresses. A real protocol needs it (a client may
     * possess a vehicle, a turret, a pet), and carrying it is also what makes ownership
     * something the server can check rather than something the transport assumes.
     */
    fun encodeInput(writer: PacketWriter, netId: Long, tick: Long, moveX: Float, moveY: Float) {
        writer.reset()
        writer.writeByte(Opcode.INPUT)
        writer.writeVarLong(netId)
        writer.writeVarLong(tick)
        writer.writeShort(quantizeMove(moveX))
        writer.writeShort(quantizeMove(moveY))
    }

    /** Returns false on a malformed packet; the reader is left unusable but never throws. */
    fun decodeInput(reader: PacketReader, into: InputBuffer): Boolean {
        if (reader.readByte() != Opcode.INPUT) return false
        into.netId = reader.readVarLong()
        into.tick = reader.readVarLong()
        into.moveX = reader.readShort() / MOVE_SCALE
        into.moveY = reader.readShort() / MOVE_SCALE
        return reader.ok && reader.remaining == 0
    }

    fun encodeSnapshot(writer: PacketWriter, tick: Long, players: SnapshotBuffer) {
        writer.reset()
        writer.writeByte(Opcode.SNAPSHOT)
        writer.writeVarLong(tick)
        writer.writeVarInt(players.count)
        for (index in 0 until players.count) {
            writer.writeVarLong(players.netIds[index])
            writer.writeZigZagInt((players.x[index] * POSITION_SCALE).roundToInt())
            writer.writeZigZagInt((players.y[index] * POSITION_SCALE).roundToInt())
        }
    }

    /**
     * Decodes into [into], reusing its arrays. Allocates only when the snapshot is larger than
     * any seen before, which in a running game happens a handful of times and then never.
     */
    fun decodeSnapshot(reader: PacketReader, into: SnapshotBuffer): Boolean {
        // Reading the header before validating it is safe: every read is bounds-checked, so a
        // wrong opcode costs three cheap reads and no allocation.
        val opcode = reader.readByte()
        val tick = reader.readVarLong()
        val count = reader.readVarInt()
        val countInRange = count in 0..MAX_PLAYERS_PER_SNAPSHOT
        if (opcode != Opcode.SNAPSHOT || !reader.ok || !countInRange) return false
        into.reset(tick, count)
        for (index in 0 until count) {
            into.netIds[index] = reader.readVarLong()
            into.x[index] = reader.readZigZagInt() / POSITION_SCALE
            into.y[index] = reader.readZigZagInt() / POSITION_SCALE
        }
        return reader.ok && reader.remaining == 0
    }

    private fun quantizeMove(value: Float): Short =
        (value.coerceIn(-1f, 1f) * MOVE_SCALE).roundToInt().toShort()
}

/** Reusable decode target. Parallel arrays, so decoding a snapshot allocates nothing. */
class SnapshotBuffer(initialCapacity: Int = 16) {
    var tick: Long = -1L
        private set

    var count: Int = 0
        private set

    var netIds: LongArray = LongArray(initialCapacity)
        private set

    var x: FloatArray = FloatArray(initialCapacity)
        private set

    var y: FloatArray = FloatArray(initialCapacity)
        private set

    fun reset(tick: Long, count: Int) {
        ensureCapacity(count)
        this.tick = tick
        this.count = count
    }

    /** Appends one entity, growing if needed. Used by the server when building a snapshot. */
    fun add(netId: Long, x: Float, y: Float) {
        ensureCapacity(count + 1)
        netIds[count] = netId
        this.x[count] = x
        this.y[count] = y
        count += 1
    }

    fun clear() {
        count = 0
    }

    /** Overwrites this buffer from [other], reusing arrays. */
    fun copyFrom(other: SnapshotBuffer) {
        reset(other.tick, other.count)
        other.netIds.copyInto(netIds, endIndex = other.count)
        other.x.copyInto(x, endIndex = other.count)
        other.y.copyInto(y, endIndex = other.count)
    }

    fun indexOf(netId: Long): Int {
        for (index in 0 until count) {
            if (netIds[index] == netId) return index
        }
        return -1
    }

    private fun ensureCapacity(required: Int) {
        if (required <= netIds.size) return
        var capacity = netIds.size
        while (capacity < required) capacity *= 2
        netIds = netIds.copyOf(capacity)
        x = x.copyOf(capacity)
        y = y.copyOf(capacity)
    }
}

/** Reusable decode target for one input frame. */
class InputBuffer {
    var netId: Long = 0
    var tick: Long = 0
    var moveX: Float = 0f
    var moveY: Float = 0f
}

