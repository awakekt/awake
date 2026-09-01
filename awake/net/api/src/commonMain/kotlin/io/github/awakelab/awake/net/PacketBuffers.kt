/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.net

/**
 * Growable write cursor. One instance per sender, reused across ticks; [toPacket] is the only
 * allocation, and it exists because transports want an exactly-sized array.
 *
 * Deliberately format-agnostic: it offers varints and zig-zag because those are the primitives
 * every compact protocol needs, and nothing about entities, positions or opcodes, because
 * those are the consuming game's business.
 */
class PacketWriter(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity)

    var size: Int = 0
        private set

    fun reset() {
        size = 0
    }

    fun writeByte(value: Byte) {
        ensure(1)
        buffer[size++] = value
    }

    fun writeShort(value: Short) {
        ensure(2)
        val raw = value.toInt()
        buffer[size++] = (raw ushr 8).toByte()
        buffer[size++] = raw.toByte()
    }

    fun writeVarInt(value: Int) {
        var remaining = value
        while (remaining and CONTINUATION_MASK.inv() != 0) {
            writeByte(((remaining and CONTINUATION_MASK) or CONTINUATION_BIT).toByte())
            remaining = remaining ushr SHIFT
        }
        writeByte(remaining.toByte())
    }

    fun writeVarLong(value: Long) {
        var remaining = value
        while (remaining and CONTINUATION_MASK.toLong().inv() != 0L) {
            writeByte(((remaining and CONTINUATION_MASK.toLong()) or CONTINUATION_BIT.toLong()).toByte())
            remaining = remaining ushr SHIFT
        }
        writeByte(remaining.toByte())
    }

    /** Zig-zag so small negative values cost one byte, not five. */
    fun writeZigZagInt(value: Int) {
        writeVarInt((value shl 1) xor (value shr Int.SIZE_BITS - 1))
    }

    fun toPacket(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        if (size + extra <= buffer.size) return
        var capacity = buffer.size
        while (capacity < size + extra) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }

    private companion object {
        const val CONTINUATION_MASK = 0x7F
        const val CONTINUATION_BIT = 0x80
        const val SHIFT = 7
    }
}

/**
 * Read cursor over a received packet. Every read is bounds-checked; underflow sets [ok] to
 * false and returns zero rather than throwing, so a hostile packet costs no exception object
 * and no stack trace -- the cheap failure is the point, since flooding malformed packets is
 * itself an attack.
 *
 * A decoder built on this must still bound its own length prefixes before allocating: this
 * class guarantees you cannot read past the packet, not that the packet is sane.
 */
class PacketReader {
    private var buffer: ByteArray = EMPTY
    private var limit: Int = 0
    private var position: Int = 0

    var ok: Boolean = true
        private set

    val remaining: Int get() = limit - position

    fun reset(packet: ByteArray, length: Int = packet.size) {
        buffer = packet
        limit = length.coerceAtMost(packet.size)
        position = 0
        ok = true
    }

    fun readByte(): Byte {
        if (position >= limit) return fail()
        return buffer[position++]
    }

    fun readShort(): Short {
        if (position + 2 > limit) return fail().toShort()
        val high = buffer[position++].toInt() and BYTE_MASK
        val low = buffer[position++].toInt() and BYTE_MASK
        return ((high shl 8) or low).toShort()
    }

    fun readVarInt(): Int = readVarLong().toInt()

    fun readVarLong(): Long {
        var result = 0L
        var shift = 0
        var done = false
        while (!done) {
            // An over-long varint is malformed, not a huge number -- treat it as underflow.
            if (shift > MAX_SHIFT || position >= limit) {
                fail()
                result = 0L
                done = true
            } else {
                val byte = buffer[position++].toInt()
                result = result or ((byte and CONTINUATION_MASK).toLong() shl shift)
                if (byte and CONTINUATION_BIT == 0) done = true else shift += SHIFT
            }
        }
        return result
    }

    fun readZigZagInt(): Int {
        val raw = readVarInt()
        return (raw ushr 1) xor -(raw and 1)
    }

    private fun fail(): Byte {
        ok = false
        position = limit
        return 0
    }

    private companion object {
        val EMPTY = ByteArray(0)
        const val BYTE_MASK = 0xFF
        const val CONTINUATION_MASK = 0x7F
        const val CONTINUATION_BIT = 0x80
        const val SHIFT = 7
        const val MAX_SHIFT = 63
    }
}
