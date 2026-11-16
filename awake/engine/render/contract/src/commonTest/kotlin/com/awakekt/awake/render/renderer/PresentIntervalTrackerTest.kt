/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The time between presented frames, and how it reaches the frame stats (#1699). */
class PresentIntervalTrackerTest {
    private val frame30 = 33_333_333L
    private val frame60 = 16_666_667L

    @Test
    fun nothingIsReportedBeforeTheSecondPresent() {
        val tracker = PresentIntervalTracker()
        assertNull(tracker.percentileNanos(50))
        tracker.recordPresentAt(0L)
        assertNull(tracker.percentileNanos(50))
        tracker.recordPresentAt(frame60)
        assertEquals(frame60, tracker.percentileNanos(50))
    }

    @Test
    fun aSteadyCapReadsTheSameAtTheMedianAndTheTail() {
        val tracker = PresentIntervalTracker()
        presents(tracker, List(60) { frame30 })
        assertEquals(frame30, tracker.percentileNanos(50))
        assertEquals(frame30, tracker.percentileNanos(95))
    }

    /** The pacing the sleep loop can give a 30 fps cap on a 60 Hz panel: the same mean, a wide spread. */
    @Test
    fun unevenPacingShowsAsTheGapBetweenTheMedianAndTheTail() {
        val tracker = PresentIntervalTracker()
        presents(tracker, List(60) { if (it % 4 == 0) 3 * frame60 else frame60 + frame60 / 3 })
        val p50 = tracker.percentileNanos(50)!!
        val p95 = tracker.percentileNanos(95)!!
        assertTrue(p95 - p50 > frame60, "p50 $p50, p95 $p95")
    }

    @Test
    fun theWindowKeepsOnlyTheMostRecentIntervals() {
        val tracker = PresentIntervalTracker(windowSize = 4)
        presents(tracker, List(10) { frame60 } + List(4) { frame30 })
        assertEquals(4, tracker.count)
        assertEquals(frame30, tracker.percentileNanos(0))
    }

    @Test
    fun aResetDropsTheGapAcrossIt() {
        val tracker = PresentIntervalTracker()
        tracker.recordPresentAt(0L)
        tracker.recordPresentAt(frame60)
        tracker.reset()
        tracker.recordPresentAt(5_000_000_000L)
        assertNull(tracker.percentileNanos(95), "the five seconds a rebuild took is not an interval")
    }

    @Test
    fun publishCarriesTheSpreadAndWhereItWasMeasured() {
        val counter = RenderStatsCounter()
        counter.publish()
        assertNull(counter.latest?.presentTiming, "a backend that does not measure reports nothing")
        val tracker = PresentIntervalTracker(timing = PresentTiming.DisplayTiming)
        counter.presentIntervals = tracker
        counter.publish()
        assertNull(counter.latest?.presentTiming, "nor before its second present")
        repeat(3) { tracker.recordInterval(frame30) }
        counter.publish()
        val stats = counter.latest!!
        assertEquals(frame30, stats.presentIntervalP50Ns)
        assertEquals(frame30, stats.presentIntervalP95Ns)
        assertEquals(PresentTiming.DisplayTiming, stats.presentTiming)
    }

    private fun presents(tracker: PresentIntervalTracker, intervals: List<Long>) {
        var at = 0L
        tracker.recordPresentAt(at)
        for (interval in intervals) {
            at += interval
            tracker.recordPresentAt(at)
        }
    }
}
