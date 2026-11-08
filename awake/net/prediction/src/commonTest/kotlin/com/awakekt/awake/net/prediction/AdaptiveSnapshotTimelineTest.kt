/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveSnapshotTimelineTest {
    @Test
    fun clockSkewDoesNotExhaustInterpolationBuffer() {
        for (rate in listOf(0.96, 1.01)) {
            val timeline = AdaptiveSnapshotTimeline(initialIntervalSeconds = 1.0 / 15.0)
            var tick = 0
            var latest = 0.0
            var underruns = 0
            for (frame in 0..60 * 120) {
                val client = frame / 60.0
                timeline.advance(1f / 60f)
                while (tick / 15.0 <= client * rate) {
                    latest = tick / 15.0
                    timeline.observe(client, latest)
                    tick++
                }
                if (client > 10.0 && timeline.serverTime(client) - 0.1 > latest) {
                    underruns++
                }
            }
            assertEquals(0, underruns, "rate=$rate exhausted snapshots on $underruns frames")
        }
    }

    @Test
    fun batchedReceiptsAndClockDriftNeverRewindOrDiverge() {
        val timeline = AdaptiveSnapshotTimeline(initialIntervalSeconds = 1.0 / 15.0)
        var tick = 0
        var previous = Double.NEGATIVE_INFINITY
        var latest = 0.0
        for (frame in 0..60 * 120) {
            val client = frame / 60.0
            timeline.advance(1f / 60f)
            if (frame % 4 == 0) {
                while (tick / 15.0 <= client * 1.01) {
                    latest = tick / 15.0
                    timeline.observe(client, latest)
                    tick++
                }
            }
            val server = timeline.serverTime(client)
            assertTrue(server >= previous, "serverTime rewound at frame $frame")
            previous = server
            if (client > 10.0) {
                assertTrue(abs(server - latest) < 0.15, "unbounded drift at $client: ${server - latest}")
            }
        }
    }

    @Test
    fun learnsMedianIntervalIgnoringOutlierGapAndClampsDelayToBounds() {
        val timeline = AdaptiveSnapshotTimeline(initialIntervalSeconds = 1.0 / 15.0)
        assertFalse(timeline.ready)
        assertEquals(2.0 / 15.0, timeline.delaySeconds, 1e-9)

        var tick = 0
        for (frame in 0..60 * 10) {
            val client = frame / 60.0
            timeline.advance(1f / 60f)
            while (tick / 30.0 <= client) {
                timeline.observe(client, tick / 30.0)
                tick += if (tick == 150) 10 else 1
            }
        }
        assertTrue(timeline.ready)
        assertEquals(1.0 / 30.0, timeline.intervalSeconds, 1e-9)
        assertEquals(AdaptiveSnapshotTimeline.DEFAULT_MIN_DELAY_SECONDS, timeline.delaySeconds, 1e-9)
        assertEquals(timeline.serverTime(10.0) - timeline.delaySeconds, timeline.playbackTime(10.0), 1e-12)
    }

    @Test
    fun holdFreezesPlaybackAtExtrapolationCapDuringStallAndRecoversSmoothly() {
        val timeline = AdaptiveSnapshotTimeline(initialIntervalSeconds = 1.0 / 15.0)
        val lead = 0.1
        var tick = 0
        var previous = Double.NaN
        var fastest = 0.0
        for (frame in 0..60 * 12) {
            val client = frame / 60.0
            timeline.advance(1f / 60f)
            while (tick / 15.0 <= client && !(client >= 4.0 && client < 4.5)) {
                timeline.observe(client, tick / 15.0)
                tick++
            }
            if (client >= 4.0 && client < 4.5) {
                tick = (client * 15).toInt() + 1
            }
            timeline.hold(client, lead)
            val playback = timeline.playbackTime(client)
            assertTrue(
                playback <= timeline.newestServerSeconds + lead + 1e-9,
                "frame $frame exceeded extrapolation lead",
            )
            if (!previous.isNaN()) {
                assertTrue(playback >= previous, "frame $frame rewound")
                fastest = maxOf(fastest, (playback - previous) * 60.0)
            }
            previous = playback
        }
        assertTrue(fastest <= 1.0 + AdaptiveSnapshotTimeline.CATCH_UP_RATE + 1e-6)
        assertTrue(timeline.delaySeconds <= AdaptiveSnapshotTimeline.DEFAULT_MAX_DELAY_SECONDS)
    }

    @Test
    fun droppedServerTicksTransferHeldDelayIntoOffsetWithoutOvershootingSnapshots() {
        val timeline = AdaptiveSnapshotTimeline(initialIntervalSeconds = 1.0 / 15.0)
        val lead = 0.1
        var server = 0.0
        var previous = Double.NaN
        var pastNewestAfterResume = 0
        for (frame in 0..60 * 12) {
            val client = frame / 60.0
            timeline.advance(1f / 60f)
            val behind = if (client >= 5.5) 0.5 else 0.0
            if (client < 4.0 || client >= 5.5) {
                if (client >= 5.5 && server < 4.5) {
                    server = floor((client - behind) * 15.0 - 1.0) / 15.0
                }
                while (server + 1.0 / 15.0 <= client - behind + 1e-9) {
                    server += 1.0 / 15.0
                    timeline.observe(client, server)
                }
            }
            timeline.hold(client, lead)
            val playback = timeline.playbackTime(client)
            if (!previous.isNaN()) {
                assertTrue(playback >= previous, "frame $frame rewound")
            }
            previous = playback
            if (client > 5.6 && playback > timeline.newestServerSeconds) {
                pastNewestAfterResume++
            }
        }
        assertEquals(0, pastNewestAfterResume)
        assertTrue(timeline.delaySeconds <= AdaptiveSnapshotTimeline.DEFAULT_MAX_DELAY_SECONDS)
    }

    @Test
    fun randomQueueJitterMaintainsSteadyPlaybackPace() {
        var worstDeviation = 0.0
        var windowsOverFivePercent = 0
        var totalWindows = 0
        for (seed in 1..5) {
            val timeline = AdaptiveSnapshotTimeline(initialIntervalSeconds = 1.0 / 15.0)
            val random = Random(seed)
            var tick = 0
            var lastArrival = 0.0
            var windowStart = Double.NaN
            var windowPlayback = 0.0
            for (frame in 0..60 * 60) {
                val client = frame / 60.0
                timeline.advance(1f / 60f)
                while (true) {
                    val server = tick / 15.0
                    val arrival = maxOf(lastArrival, server + random.nextDouble() * 0.08)
                    if (arrival > client) break
                    timeline.observe(client, server)
                    lastArrival = arrival
                    tick++
                }
                val playback = timeline.playbackTime(client)
                if (client < 10.0) continue
                if (windowStart.isNaN()) {
                    windowStart = client
                    windowPlayback = playback
                } else if (client - windowStart >= 0.25 - 1e-9) {
                    val paceError = abs((playback - windowPlayback) / (client - windowStart) - 1.0)
                    worstDeviation = maxOf(worstDeviation, paceError)
                    if (paceError > 0.05) windowsOverFivePercent++
                    totalWindows++
                    windowStart = client
                    windowPlayback = playback
                }
            }
        }
        assertTrue(worstDeviation < 0.12, "pace swung by $worstDeviation")
        assertTrue(windowsOverFivePercent < totalWindows / 20)
    }

    @Test
    fun longClientFramePauseRebasesImmediatelyAndResetClearsState() {
        val timeline = AdaptiveSnapshotTimeline()
        for (frame in 0..60 * 5) {
            val client = frame / 60.0
            timeline.advance(1f / 60f)
            timeline.observe(client, client)
        }
        val resumedClient = 5.25
        val resumedServer = 305.0
        for (i in 31 downTo 0) {
            timeline.observe(resumedClient, resumedServer - i * 0.05)
        }
        timeline.advance(0.25)
        val playback = timeline.playbackTime(resumedClient)
        assertTrue(abs((resumedServer - playback) - timeline.delaySeconds) < 0.05)

        timeline.reset()
        assertFalse(timeline.ready)
        assertTrue(timeline.newestServerSeconds.isNaN())
    }

    @Test
    fun rejectsInvalidParameters() {
        assertFailsWith<IllegalArgumentException> { AdaptiveSnapshotTimeline(minDelaySeconds = -0.01) }
        assertFailsWith<IllegalArgumentException> {
            AdaptiveSnapshotTimeline(minDelaySeconds = 0.3, maxDelaySeconds = 0.2)
        }
        assertFailsWith<IllegalArgumentException> { AdaptiveSnapshotTimeline(initialIntervalSeconds = 0.0) }
        val timeline = AdaptiveSnapshotTimeline()
        assertFailsWith<IllegalArgumentException> { timeline.observe(Double.NaN, 1.0) }
        assertFailsWith<IllegalArgumentException> { timeline.advance(-0.1) }
        assertFailsWith<IllegalArgumentException> { timeline.hold(1.0, -0.1) }
    }
}
