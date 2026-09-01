/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.netdemo

import com.sun.management.ThreadMXBean
import io.github.awakelab.awake.net.PacketReader
import io.github.awakelab.awake.net.PacketWriter
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Phase 1 exit criterion from docs/plans/network.md: decoding a snapshot allocates nothing.
 * Snapshot decode runs every tick on every client, so an allocation here is GC pressure inside
 * the frame loop, not a library detail.
 *
 * Same measurement idiom as `ConstraintsAllocationProbe`: JVM-only, because
 * `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class TickCodecAllocationProbe {
    private val writer = PacketWriter()
    private val reader = PacketReader()
    private val decoded = SnapshotBuffer(initialCapacity = PLAYERS)
    private val packet = buildPacket()

    @Test
    fun snapshotDecodeAllocatesNothing() {
        warmUp()
        val bytes = measure {
            repeat(OPS) {
                reader.reset(packet)
                sink += if (TickCodec.decodeSnapshot(reader, decoded)) decoded.count else 0
            }
        }
        println("decode: $bytes bytes / $OPS snapshots of $PLAYERS players")
        assertEquals(0L, bytes, "snapshot decode must not allocate; a boxed read or a temp list is the bug")
    }

    /** Encode allocates exactly one packet per tick, and that one is Ktor's API, not ours. */
    @Test
    fun snapshotEncodeAllocatesOnlyThePacket() {
        warmUp()
        val source = sourceBuffer()
        val bytes = measure {
            repeat(OPS) {
                TickCodec.encodeSnapshot(writer, tick = it.toLong(), players = source)
                sink += writer.size
            }
        }
        val perOp = bytes.toDouble() / OPS
        println("encode: $bytes bytes / $OPS snapshots = $perOp B/op")
        assertEquals(0L, bytes, "encoding into a reused writer must not allocate")
    }

    @Test
    fun decodingAForgedCountAllocatesNothing() {
        warmUp()
        val hostile = forgedCountPacket()
        val into = SnapshotBuffer()
        val bytes = measure {
            repeat(OPS) {
                reader.reset(hostile)
                sink += if (TickCodec.decodeSnapshot(reader, into)) 1 else 0
            }
        }
        println("reject: $bytes bytes / $OPS hostile packets")
        assertTrue(bytes == 0L, "rejecting a malformed packet allocated $bytes bytes; flooding them is the attack")
    }

    private fun sourceBuffer() = SnapshotBuffer(initialCapacity = PLAYERS).apply {
        repeat(PLAYERS) { add(netId = it.toLong(), x = it * STRIDE, y = -it * STRIDE) }
    }

    private fun buildPacket(): ByteArray {
        val scratch = PacketWriter()
        TickCodec.encodeSnapshot(scratch, tick = 1, players = sourceBuffer())
        return scratch.toPacket()
    }

    private fun forgedCountPacket(): ByteArray {
        val scratch = PacketWriter()
        scratch.writeByte(TickCodec.Opcode.SNAPSHOT)
        scratch.writeVarLong(1)
        scratch.writeVarInt(Int.MAX_VALUE)
        return scratch.toPacket()
    }

    /** Escapes so the JIT cannot delete the work being measured. */
    private var sink: Int = 0

    private fun warmUp() {
        val source = sourceBuffer()
        repeat(WARM_UP_ROUNDS) {
            TickCodec.encodeSnapshot(writer, tick = it.toLong(), players = source)
            reader.reset(packet)
            sink += if (TickCodec.decodeSnapshot(reader, decoded)) decoded.count else 0
        }
    }

    private inline fun measure(block: () -> Unit): Long {
        val before = allocatedBytes()
        block()
        return allocatedBytes() - before
    }

    private companion object {
        const val OPS = 100_000
        const val WARM_UP_ROUNDS = 10_000
        const val PLAYERS = 16
        const val STRIDE = 3.75f

        private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

        fun allocatedBytes(): Long = threads.currentThreadAllocatedBytes
    }
}
