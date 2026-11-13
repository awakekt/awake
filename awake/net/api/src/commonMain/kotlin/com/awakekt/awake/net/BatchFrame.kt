/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("ReturnCount")

package com.awakekt.awake.net

/**
 * Wire format of a batch: several packets one tick produced for one client, carried as the payload
 * of a single transport frame.
 *
 * ```text
 * MARKER (0xB7) | VERSION (1) | varint count | count x ( varint length | length bytes )
 * ```
 *
 * A batch is never sent to a peer that has not asked for it: [BatchNegotiated] says whether the peer
 * connected with [NEGOTIATION_PARAMETER]`=`[NEGOTIATION_VALUE], and a [BatchedSession] to a peer
 * that did not passes every packet straight through, as a session without it does. So an older
 * client, which cannot tell a batch from a game packet, never receives one. A peer that did ask
 * receives *only* batches (a single packet is a batch of one), so it never has to guess.
 */
object BatchFrame {
    /** First byte of every batch frame. */
    const val MARKER: Int = 0xB7

    /** Format version, the second byte; a reader rejects any other. */
    const val VERSION: Int = 1

    /** Query parameter a client adds to the connect URL to ask for batched frames. */
    const val NEGOTIATION_PARAMETER: String = "awake-batch"

    /** The [NEGOTIATION_PARAMETER] value that asks for the version this format defines. */
    const val NEGOTIATION_VALUE: String = "1"

    /** Bytes of marker and version before the count. */
    const val HEADER_BYTES: Int = 2

    private const val SEVEN_BITS = 0x7F
    private const val CONTINUE = 0x80
    private const val SHIFT = 7
    private const val BYTE = 0xFF
    private const val MAX_VARINT_BYTES = 5

    /** The bytes [length] takes as a varint. */
    fun varIntSize(length: Int): Int {
        var size = 1
        var rest = length ushr SHIFT
        while (rest != 0) {
            size++
            rest = rest ushr SHIFT
        }
        return size
    }

    /** The bytes one entry of [length] takes inside a batch: its length prefix and its payload. */
    fun entrySize(length: Int): Int = varIntSize(length) + length

    /**
     * Encodes `packets[from until to]` as one batch.
     *
     * @param packets Staged packets, in the order to deliver them.
     * @param from First index, inclusive.
     * @param to Last index, exclusive.
     * @return The batch frame payload.
     */
    fun encode(packets: List<ByteArray>, from: Int = 0, to: Int = packets.size): ByteArray {
        require(from in 0..to && to <= packets.size) { "range $from..$to is outside 0..${packets.size}" }
        var size = HEADER_BYTES + varIntSize(to - from)
        for (i in from until to) size += entrySize(packets[i].size)
        val out = ByteArray(size)
        out[0] = MARKER.toByte()
        out[1] = VERSION.toByte()
        var at = writeVarInt(out, HEADER_BYTES, to - from)
        for (i in from until to) {
            val packet = packets[i]
            at = writeVarInt(out, at, packet.size)
            packet.copyInto(out, at)
            at += packet.size
        }
        return out
    }

    /**
     * Splits a batch into its packets, in order.
     *
     * @param frame A frame payload received from a peer that negotiated batching.
     * @return The packets, or `null` when [frame] is not a well-formed batch (wrong marker or
     *   version, a count or length that runs past the frame, or bytes left over). Nothing is
     *   delivered from a malformed batch, so a reader never acts on half of one.
     */
    fun decode(frame: ByteArray): List<ByteArray>? {
        if (frame.size < HEADER_BYTES + 1 ||
            (frame[0].toInt() and BYTE) != MARKER ||
            (frame[1].toInt() and BYTE) != VERSION
        ) {
            return null
        }
        val cursor = intArrayOf(HEADER_BYTES)
        val count = readVarInt(frame, cursor)
        // Every entry takes at least its length byte, so a larger count cannot be honest.
        if (count < 0 || count > frame.size - cursor[0]) return null
        val packets = ArrayList<ByteArray>(count)
        repeat(count) {
            val length = readVarInt(frame, cursor)
            if (length < 0 || length > frame.size - cursor[0]) return null
            packets += frame.copyOfRange(cursor[0], cursor[0] + length)
            cursor[0] += length
        }
        return if (cursor[0] == frame.size) packets else null
    }

    /**
     * Frames entries already laid out as `varint length | bytes` in [entries] (`from until to`, `count`
     * of them) into one exactly sized batch: the only array a flush allocates.
     */
    internal fun frameEntries(entries: ByteArray, from: Int, to: Int, count: Int): ByteArray {
        val out = ByteArray(HEADER_BYTES + varIntSize(count) + (to - from))
        out[0] = MARKER.toByte()
        out[1] = VERSION.toByte()
        val at = writeVarInt(out, HEADER_BYTES, count)
        entries.copyInto(out, at, from, to)
        return out
    }

    /** Writes [value] as a varint at [start] and returns the index after it. */
    internal fun writeVarInt(out: ByteArray, start: Int, value: Int): Int {
        var at = start
        var rest = value
        while (rest ushr SHIFT != 0) {
            out[at++] = ((rest and SEVEN_BITS) or CONTINUE).toByte()
            rest = rest ushr SHIFT
        }
        out[at++] = rest.toByte()
        return at
    }

    /** Reads a varint at `cursor[0]` and advances it; -1 when it is cut short or too long. */
    private fun readVarInt(frame: ByteArray, cursor: IntArray): Int {
        var result = 0
        var shift = 0
        var at = cursor[0]
        repeat(MAX_VARINT_BYTES) {
            if (at >= frame.size) return -1
            val byte = frame[at++].toInt() and BYTE
            result = result or ((byte and SEVEN_BITS) shl shift)
            if (byte and CONTINUE == 0) {
                cursor[0] = at
                return result
            }
            shift += SHIFT
        }
        return -1
    }
}

/**
 * Optional capability of a [TransportSession]: whether its peer asked for batched frames at connect
 * (see [BatchFrame]). An optional capability keeps existing [TransportSession] implementations
 * binary-compatible; read it through [acceptsBatches].
 */
interface BatchNegotiated {
    /** `true` when the peer connected asking for [BatchFrame] frames. Fixed for the session. */
    val clientAcceptsBatches: Boolean
}

/** Whether this session's peer asked for batched frames; `false` for a transport that does not say. */
val TransportSession.acceptsBatches: Boolean
    get() = (this as? BatchNegotiated)?.clientAcceptsBatches == true
