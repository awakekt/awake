/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacketBuffersTest {
    private val writer = PacketWriter()
    private val reader = PacketReader()

    @Test
    fun varLongRoundTripsAcrossTheWholeRange() {
        val values = longArrayOf(0, 1, 127, 128, 300, Int.MAX_VALUE.toLong(), Long.MAX_VALUE)
        writer.reset()
        values.forEach { writer.writeVarLong(it) }

        reader.reset(writer.toPacket())
        values.forEach { assertEquals(it, reader.readVarLong()) }
        assertTrue(reader.ok)
        assertEquals(0, reader.remaining)
    }

    @Test
    fun zigZagKeepsSmallNegativesCheap() {
        writer.reset()
        writer.writeZigZagInt(-1)
        assertEquals(1, writer.size, "a small negative must cost one byte, or the encoding is pointless")

        writer.reset()
        listOf(0, -1, 1, -1_000, 1_000, Int.MIN_VALUE, Int.MAX_VALUE).forEach { writer.writeZigZagInt(it) }
        reader.reset(writer.toPacket())
        listOf(0, -1, 1, -1_000, 1_000, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertEquals(it, reader.readZigZagInt())
        }
        assertTrue(reader.ok)
    }

    @Test
    fun shortRoundTripsIncludingNegatives() {
        writer.reset()
        writer.writeShort(-32_768)
        writer.writeShort(32_767)
        reader.reset(writer.toPacket())
        assertEquals(-32_768, reader.readShort())
        assertEquals(32_767, reader.readShort())
    }

    @Test
    fun writerGrowsPastItsInitialCapacity() {
        val small = PacketWriter(initialCapacity = 2)
        repeat(GROWTH_WRITES) { small.writeByte(it.toByte()) }
        assertEquals(GROWTH_WRITES, small.size)
        assertEquals(1, small.toPacket()[1])
    }

    @Test
    fun readingPastTheEndFailsInsteadOfThrowing() {
        reader.reset(byteArrayOf(1))
        assertEquals(1, reader.readByte())
        assertEquals(0, reader.readByte())
        assertFalse(reader.ok, "underflow must set ok, not throw")
    }

    /** A length field must never be honoured beyond the packet the reader was given. */
    @Test
    fun limitClampsToTheDeclaredLength() {
        reader.reset(byteArrayOf(1, 2, 3, 4), length = 2)
        assertEquals(2, reader.remaining)
        reader.readByte()
        reader.readByte()
        reader.readByte()
        assertFalse(reader.ok)
    }

    /** An over-long varint is a malformed packet, not a large number. */
    @Test
    fun overLongVarintIsRejected() {
        val allContinuations = ByteArray(OVER_LONG_BYTES) { 0xFF.toByte() }
        reader.reset(allContinuations)
        reader.readVarLong()
        assertFalse(reader.ok, "an unterminated varint was accepted")
    }

    @Test
    fun resetClearsTheFailureFlag() {
        reader.reset(ByteArray(0))
        reader.readByte()
        assertFalse(reader.ok)

        reader.reset(byteArrayOf(7))
        assertTrue(reader.ok)
        assertEquals(7, reader.readByte())
    }

    private companion object {
        const val GROWTH_WRITES = 100
        const val OVER_LONG_BYTES = 12
    }
}
