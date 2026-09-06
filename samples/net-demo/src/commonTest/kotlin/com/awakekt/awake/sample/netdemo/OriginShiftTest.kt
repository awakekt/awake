/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.netdemo

import com.awakekt.awake.net.PacketReader
import com.awakekt.awake.net.PacketWriter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 2's origin-shift criterion.
 *
 * `FloatingOriginSystem` shifts the render origin per client, so two clients can disagree about
 * where zero is at the same instant. A snapshot must therefore carry world-absolute
 * coordinates: if positions were ever encoded relative to the sender's origin, a peer that had
 * shifted differently would decode them kilometres out, and nothing in the protocol would
 * complain. This test is the guard, because that failure is silent by construction.
 */
class OriginShiftTest {
    private val writer = PacketWriter()
    private val reader = PacketReader()

    @Test
    fun `decoded positions do not depend on the receiver's origin`() {
        val world = SnapshotBuffer().apply {
            reset(tick = 7, count = 0)
            add(netId = 1, x = 1_000.5f, y = -2_000.25f)
            add(netId = 2, x = 0f, y = 0f)
        }
        TickCodec.encodeSnapshot(writer, tick = 7, players = world)
        val packet = writer.toPacket()

        // Two receivers whose render origins have shifted to different cells decode the same
        // bytes; the decoded world position must be identical for both.
        val first = decode(packet)
        val second = decode(packet)
        for (index in 0 until first.count) {
            assertTrue(abs(first.x[index] - second.x[index]) < EPSILON)
            assertTrue(abs(first.y[index] - second.y[index]) < EPSILON)
        }

        // And it must match the authored world position, not an origin-relative one.
        val entity = first.indexOf(1)
        assertTrue(abs(first.x[entity] - 1_000.5f) < QUANTIZATION, "x is not world-absolute: ${first.x[entity]}")
        assertTrue(abs(first.y[entity] + 2_000.25f) < QUANTIZATION, "y is not world-absolute: ${first.y[entity]}")
    }

    /** Far-from-origin coordinates must survive quantization, since that is where an open world lives. */
    @Test
    fun `far from origin positions keep centimetre precision`() {
        val far = SnapshotBuffer().apply {
            reset(tick = 1, count = 0)
            add(netId = 1, x = FAR_METRES, y = -FAR_METRES)
        }
        TickCodec.encodeSnapshot(writer, tick = 1, players = far)
        val decoded = decode(writer.toPacket())

        assertTrue(
            abs(decoded.x[0] - FAR_METRES) < QUANTIZATION,
            "precision collapsed ${FAR_METRES}m from the origin: ${decoded.x[0]}",
        )
    }

    private fun decode(packet: ByteArray): SnapshotBuffer {
        reader.reset(packet)
        val into = SnapshotBuffer()
        assertTrue(TickCodec.decodeSnapshot(reader, into))
        return into
    }

    private companion object {
        const val EPSILON = 0.0001f

        /** Half the centimetre step, plus slack for Float representation. */
        const val QUANTIZATION = 0.02f

        /** 20 km out: past where Float metres start losing centimetres. */
        const val FAR_METRES = 20_000f
    }
}
