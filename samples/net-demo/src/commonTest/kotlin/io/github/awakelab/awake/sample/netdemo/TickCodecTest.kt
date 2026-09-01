/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import io.github.awakelab.awake.net.PacketReader
import io.github.awakelab.awake.net.PacketWriter
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TickCodecTest {
    private val writer = PacketWriter()
    private val reader = PacketReader()

    @Test
    fun `snapshot round-trips within the quantization step`() {
        val source = SnapshotBuffer().apply {
            add(netId = 1, x = 0f, y = 0f)
            add(netId = 2, x = -12.345f, y = 987.654f)
            add(netId = 300, x = 1_234.56f, y = -0.004f)
        }

        TickCodec.encodeSnapshot(writer, tick = 4_242, players = source)
        reader.reset(writer.toPacket())
        val decoded = SnapshotBuffer()
        assertTrue(TickCodec.decodeSnapshot(reader, decoded))

        assertEquals(4_242L, decoded.tick)
        assertEquals(source.count, decoded.count)
        for (index in 0 until source.count) {
            assertEquals(source.netIds[index], decoded.netIds[index])
            assertTrue(
                abs(source.x[index] - decoded.x[index]) <= POSITION_TOLERANCE,
                "x drifted at $index: ${source.x[index]} -> ${decoded.x[index]}",
            )
            assertTrue(abs(source.y[index] - decoded.y[index]) <= POSITION_TOLERANCE, "y drifted at $index")
        }
    }

    @Test
    fun `input round-trips and clamps to unit length`() {
        TickCodec.encodeInput(writer, netId = 42, tick = 7, moveX = 1f, moveY = -1f)
        reader.reset(writer.toPacket())
        val decoded = InputBuffer()
        assertTrue(TickCodec.decodeInput(reader, decoded))

        assertEquals(42L, decoded.netId, "the addressed entity must survive the round trip")
        assertEquals(7L, decoded.tick)
        assertTrue(abs(1f - decoded.moveX) <= MOVE_EPSILON)
        assertTrue(abs(-1f - decoded.moveY) <= MOVE_EPSILON)
    }

    /** A three-entity snapshot must not cost more than a JSON one did per entity. */
    @Test
    fun `snapshot stays small`() {
        val players = SnapshotBuffer().apply {
            repeat(SMALL_SNAPSHOT_PLAYERS) { add(netId = it.toLong(), x = it * 1.5f, y = -it * 2.5f) }
        }
        TickCodec.encodeSnapshot(writer, tick = 1, players = players)

        val bytesPerPlayer = writer.size.toDouble() / SMALL_SNAPSHOT_PLAYERS
        println("snapshot: ${writer.size} bytes for $SMALL_SNAPSHOT_PLAYERS players ($bytesPerPlayer B/player)")
        assertTrue(bytesPerPlayer < MAX_BYTES_PER_PLAYER, "codec regressed to $bytesPerPlayer B/player")
    }

    /**
     * A forged entity count must be rejected before the buffer grows. Without the bound this
     * asks the decoder for a two-billion-entry allocation.
     */
    @Test
    fun `forged entity count is rejected without allocating`() {
        writer.reset()
        writer.writeByte(TickCodec.Opcode.SNAPSHOT)
        writer.writeVarLong(1)
        writer.writeVarInt(Int.MAX_VALUE)
        reader.reset(writer.toPacket())

        val into = SnapshotBuffer()
        assertFalse(TickCodec.decodeSnapshot(reader, into), "forged count was accepted")
        assertEquals(0, into.count)
    }

    @Test
    fun `truncated snapshot is rejected`() {
        val players = SnapshotBuffer().apply { add(netId = 9, x = 3f, y = 4f) }
        TickCodec.encodeSnapshot(writer, tick = 2, players = players)
        val truncated = writer.toPacket().copyOf(writer.size - 1)

        reader.reset(truncated)
        assertFalse(TickCodec.decodeSnapshot(reader, SnapshotBuffer()))
    }

    @Test
    fun `trailing garbage is rejected`() {
        val players = SnapshotBuffer().apply { add(netId = 9, x = 3f, y = 4f) }
        TickCodec.encodeSnapshot(writer, tick = 2, players = players)
        val padded = writer.toPacket().copyOf(writer.size + 1)

        reader.reset(padded)
        assertFalse(TickCodec.decodeSnapshot(reader, SnapshotBuffer()))
    }

    /** Random bytes must never crash the decoder, and must never be silently accepted as huge. */
    @Test
    fun `random packets never crash the decoder`() {
        val random = Random(seed = 1_234)
        val into = SnapshotBuffer()
        var accepted = 0
        repeat(FUZZ_ROUNDS) {
            val packet = ByteArray(random.nextInt(1, FUZZ_MAX_BYTES)) { random.nextInt().toByte() }
            packet[0] = TickCodec.Opcode.SNAPSHOT
            reader.reset(packet)
            if (TickCodec.decodeSnapshot(reader, into)) accepted += 1
            assertTrue(into.count <= TickCodec.MAX_PLAYERS_PER_SNAPSHOT, "decoder accepted an unbounded count")
        }
        println("fuzz: $accepted/$FUZZ_ROUNDS random packets happened to be well-formed")
    }

    private companion object {
        /**
         * Half a centimetre is the quantization step's own bound; the extra slop covers Float
         * representation, which pushes a value landing exactly on a half-step (-12.345) just
         * past it. Tightening this below the step is how you get a test that fails on data,
         * not on defects.
         */
        const val POSITION_TOLERANCE = 0.0051f
        const val MOVE_EPSILON = 0.0001f
        const val SMALL_SNAPSHOT_PLAYERS = 8
        const val MAX_BYTES_PER_PLAYER = 8.0
        const val FUZZ_ROUNDS = 5_000
        const val FUZZ_MAX_BYTES = 64
    }
}
