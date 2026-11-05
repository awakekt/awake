/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotTimelineTest {
    @Test
    fun validatesConfigurationAndStepInputs() {
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline(bufferSeconds = -0.01) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline(bufferSeconds = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline(gain = -0.1) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline(gain = 1.1) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline(gain = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline(maxBehindSeconds = 0.0) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline().step(-0.01) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline().observe(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SnapshotTimeline().observe(1.0, arrivalLagSeconds = -0.1) }
    }

    @Test
    fun locksOnFirstSnapshotAndAdvancesWithFrameSteps() {
        val timeline = SnapshotTimeline(bufferSeconds = 0.1, gain = 0.1, maxBehindSeconds = 2.0)
        assertFalse(timeline.ready)
        assertEquals(0.0, timeline.step(0.05), EPSILON)
        assertEquals(0.05, timeline.clientTime, EPSILON)

        timeline.observe(serverTimeSeconds = 100.0)
        assertTrue(timeline.ready)
        assertEquals(99.9, timeline.playbackTime, EPSILON)
        assertEquals(0L, timeline.rebaseCount)

        assertEquals(99.95, timeline.step(0.05), EPSILON)
        assertEquals(100.0, timeline.step(0.05), EPSILON)
    }

    @Test
    fun arrivalLagCompensatesForLocalWorkerQueueDelay() {
        val timeline = SnapshotTimeline(bufferSeconds = 0.1, gain = 0.1, maxBehindSeconds = 2.0)
        // Packet stamped 100.0 waited 0.2 s on a worker queue, so server is already at 100.2 now.
        timeline.observe(serverTimeSeconds = 100.0, arrivalLagSeconds = 0.2)
        assertEquals(100.1, timeline.playbackTime, EPSILON)

        // Next frame (dt = 0.05 s), a live packet arrives with zero queue lag at serverTime = 100.25.
        timeline.step(0.05)
        timeline.observe(serverTimeSeconds = 100.25, arrivalLagSeconds = 0.0)
        assertEquals(100.15, timeline.playbackTime, EPSILON)
        assertEquals(0L, timeline.rebaseCount)
    }

    @Test
    fun smoothsNormalJitterWithoutSteppingPlaybackTimeBackwards() {
        val timeline = SnapshotTimeline(bufferSeconds = 0.1, gain = 0.1, maxBehindSeconds = 2.0)
        timeline.observe(serverTimeSeconds = 50.0)
        assertEquals(49.9, timeline.step(0.0), EPSILON)

        timeline.step(0.05)
        val beforeLatePacket = timeline.playbackTime
        assertEquals(49.95, beforeLatePacket, EPSILON)

        // Packet arrives 80 ms late (serverTime = 49.97 instead of 50.05), well inside maxBehindSeconds.
        timeline.observe(serverTimeSeconds = 49.97)
        assertEquals(0L, timeline.rebaseCount)
        assertTrue(timeline.playbackTime >= beforeLatePacket, "playbackTime must not jump backwards on jitter")

        // Subsequent normal frames continue advancing monotonically.
        val next = timeline.step(0.05)
        assertTrue(next > beforeLatePacket)
    }

    @Test
    fun rebasesImmediatelyAfterLongClampedClientPause() {
        val timeline = SnapshotTimeline(bufferSeconds = 0.1, gain = 0.1, maxBehindSeconds = 2.0)
        timeline.observe(serverTimeSeconds = 100.0)
        timeline.step(0.05)
        assertEquals(99.95, timeline.playbackTime, EPSILON)

        // Client pauses for 5 minutes (300 s); frame delta is clamped to 0.1 s while server advances +300.1 s.
        timeline.step(0.1)
        timeline.observe(serverTimeSeconds = 400.15)

        assertEquals(1L, timeline.rebaseCount)
        assertEquals(400.05, timeline.playbackTime, EPSILON)

        // Steady frames resume cleanly from the rebased timeline.
        assertEquals(400.10, timeline.step(0.05), EPSILON)
    }

    @Test
    fun burstDrainAfterPauseLocksOntoNewestSnapshotInBatch() {
        val timeline = SnapshotTimeline(bufferSeconds = 0.1, gain = 0.1, maxBehindSeconds = 2.0)
        timeline.observe(serverTimeSeconds = 10.0)
        timeline.step(1.0)

        // Client stalls for 300 s; 32 buffered snapshots (307.9 .. 311.0) are drained in the same frame.
        timeline.step(0.1)
        for (i in 0 until BURST_SNAPSHOTS) {
            val serverTime = 307.9 + i * 0.1
            timeline.observe(serverTimeSeconds = serverTime)
        }

        assertEquals(1L, timeline.rebaseCount, "entire same-frame burst counts as one rebase")
        assertEquals(310.9, timeline.playbackTime, EPSILON, "locks onto newest snapshot (311.0 - 0.1)")
    }

    @Test
    fun explicitClockObserveAtAndBoundedAdvanceHandlePausesInBothDirections() {
        val timeline = SnapshotTimeline(bufferSeconds = 0.1, gain = 0.1, maxBehindSeconds = 2.0)
        timeline.observeAt(serverTimeSeconds = 100.0, arrivalTimeSeconds = 10.0)
        assertEquals(99.9, timeline.playbackTime, EPSILON)

        // Both wall clocks jump +300 s after sleep while maxStepSeconds is capped to 0.1 s.
        timeline.observeAt(serverTimeSeconds = 400.0, arrivalTimeSeconds = 310.0)
        assertEquals(399.9, timeline.playbackTime, EPSILON)
        assertEquals(1L, timeline.rebaseCount)

        // Within maxBehindSeconds, maxStepSeconds bounds per-frame catch-up.
        val stepped = timeline.advance(clientTimeSeconds = 310.5, maxStepSeconds = 0.1)
        assertEquals(400.0, stepped, EPSILON)

        // Server instance restart resets serverTime backwards from 400 to 0 -> rebases cleanly.
        timeline.observeAt(serverTimeSeconds = 1.0, arrivalTimeSeconds = 310.6)
        assertEquals(0.9, timeline.playbackTime, EPSILON)
        assertEquals(2L, timeline.rebaseCount)

        timeline.reset()
        assertFalse(timeline.ready)
        assertEquals(0.0, timeline.playbackTime)
        assertEquals(0.0, timeline.clientTime)
        assertEquals(0L, timeline.rebaseCount)
    }

    private companion object {
        const val EPSILON = 1e-9
        const val BURST_SNAPSHOTS = 32
    }
}
