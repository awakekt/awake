/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A pause rebase keeps the learned snapshot interval, and the clamping mode absorbs bad inputs. */
class AdaptiveSnapshotTimelineInputTest {
    @Test
    fun aPauseRebaseKeepsTheLearnedIntervalAndResetForgetsIt() {
        val timeline = AdaptiveSnapshotTimeline()
        val tick = 0.1
        var client = 0.0
        repeat(40) { i ->
            client = i * tick
            timeline.advance(tick)
            timeline.observe(client, client)
        }
        assertEquals(tick, timeline.intervalSeconds, 1e-9)

        // The tab was in the background for 30 s: snapshots resume with the server well ahead.
        timeline.observe(client + 0.1, client + 30.0)

        assertEquals(tick, timeline.intervalSeconds, 1e-9, "a pause doesn't change the server's tick rate")
        assertEquals(2 * tick, timeline.delaySeconds, 1e-9, "the delay resumes from the learned interval, not the initial one")

        timeline.reset()
        assertEquals(AdaptiveSnapshotTimeline.DEFAULT_INITIAL_INTERVAL_SECONDS, timeline.intervalSeconds)
    }

    @Test
    fun theClampingModeSkipsBadCallsAndCountsThem() {
        val clamped = AdaptiveSnapshotTimeline().apply { inputMode = TimelineInputMode.Clamp }
        val reference = AdaptiveSnapshotTimeline()
        for (timeline in listOf(clamped, reference)) {
            repeat(30) { i ->
                timeline.advance(1.0 / 30.0)
                timeline.observe(i / 30.0, i / 30.0)
            }
        }

        clamped.advance(-0.5)
        clamped.advance(Double.NaN)
        clamped.advance(Double.POSITIVE_INFINITY)
        clamped.advance(Float.NaN)
        clamped.observe(Double.NaN, 1.0)
        clamped.observe(1.0, Double.NEGATIVE_INFINITY)
        clamped.hold(Double.NaN, 0.1)
        clamped.hold(1.0, Double.POSITIVE_INFINITY)

        assertEquals(8L, clamped.clampedInputs)
        assertEquals(reference.playbackTime(1.0), clamped.playbackTime(1.0), "skipped calls change nothing")
        assertEquals(reference.newestServerSeconds, clamped.newestServerSeconds)
        assertEquals(reference.delaySeconds, clamped.delaySeconds)
    }

    @Test
    fun aNegativeLeadHoldsAtTheNewestSnapshotWhenClamped() {
        val clamped = AdaptiveSnapshotTimeline().apply { inputMode = TimelineInputMode.Clamp }
        val reference = AdaptiveSnapshotTimeline()
        for (timeline in listOf(clamped, reference)) {
            timeline.observe(0.0, 0.0)
            timeline.advance(1.0 / 60.0)
        }

        clamped.hold(5.0, -1.0)
        reference.hold(5.0, 0.0)

        assertEquals(1L, clamped.clampedInputs)
        assertEquals(reference.delaySeconds, clamped.delaySeconds)
        assertEquals(clamped.newestServerSeconds, clamped.playbackTime(5.0), 1e-9)
    }

    @Test
    fun theDefaultModeStillRejectsAndResetClearsTheCount() {
        val timeline = AdaptiveSnapshotTimeline()
        assertEquals(TimelineInputMode.Reject, timeline.inputMode)
        assertFailsWith<IllegalArgumentException> { timeline.advance(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { timeline.observe(1.0, Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { timeline.hold(1.0, Double.POSITIVE_INFINITY) }
        assertEquals(0L, timeline.clampedInputs)

        timeline.inputMode = TimelineInputMode.Clamp
        timeline.advance(-1.0)
        assertEquals(1L, timeline.clampedInputs)
        timeline.reset()
        assertEquals(0L, timeline.clampedInputs)
    }
}
