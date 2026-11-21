/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SteadyFrameRate]'s rule (#1699): which rate a frame cost holds, and when it moves. */
class SteadyFrameRateTest {
    private val panel120 = listOf(120, 60, 40, 30)

    @Test
    fun theCandidatesAreThePanelsRatesAndWithAPacerTheirWholeIntervals() {
        assertEquals(listOf(120), SteadyFrameRate.candidates(listOf(120f), wholeIntervals = false))
        assertEquals(panel120, SteadyFrameRate.candidates(listOf(120f), wholeIntervals = true))
        assertEquals(listOf(120, 60), SteadyFrameRate.candidates(listOf(60f, 120f), wholeIntervals = false))
        assertEquals(listOf(90, 45, 30), SteadyFrameRate.candidates(listOf(90f), wholeIntervals = true))
        assertEquals(listOf(60, 30), SteadyFrameRate.candidates(listOf(59.94f), wholeIntervals = true))
        assertEquals(listOf(120, 90, 60, 45, 40, 30), SteadyFrameRate.candidates(listOf(60f, 90f, 120f), wholeIntervals = true))
    }

    @Test
    fun theStartIsTheFastestRateTheFirstFramesFit() {
        assertEquals(120, start(costMs = 7.0).targetFps) // 7 ms against 95% of 8.33 ms
        assertEquals(60, start(costMs = 14.0).targetFps) // the owner's 12-16 ms frames hold 60, not 67-83
        assertEquals(40, start(costMs = 20.0).targetFps)
        assertEquals(30, start(costMs = 40.0).targetFps) // nothing fits: the slowest
        assertEquals(SteadyRateReason.Start, start(costMs = 14.0).reason)
        assertEquals(ms(14.0), start(costMs = 14.0).costP90Nanos)
        assertEquals(panel120, start(costMs = 14.0).candidatesFps)
    }

    @Test
    fun nothingIsChosenBeforeTheFirstWindowOfFrames() {
        val selector = SteadyFrameRate(panel120)
        var clock = 0L
        repeat(SteadyFrameRate.DEFAULT_WINDOW_FRAMES - 1) { assertNull(selector.onFrame(ms(14.0), clock).also { clock += ms(16.7) }) }
        assertEquals(60, selector.onFrame(ms(14.0), clock)?.targetFps)
    }

    @Test
    fun aRateTheFramesOutgrowStepsDownOnceTheHoldIsOver() {
        val run = Run(SteadyFrameRate(panel120))
        run.frames(costMs = 7.0, seconds = 1.0)
        assertEquals(120, run.target)
        run.frames(costMs = 14.0, seconds = 0.5) // still inside the 2 s since the start
        assertEquals(120, run.target)
        run.frames(costMs = 14.0, seconds = 1.5)
        assertEquals(60, run.target)
        assertEquals(SteadyRateReason.SteppedDown, run.choice?.reason)
        assertEquals(ms(14.0), run.choice?.costP90Nanos)
    }

    @Test
    fun aStepDownGoesStraightToTheRateThatFits() {
        val run = Run(SteadyFrameRate(panel120))
        run.frames(costMs = 7.0, seconds = 2.5)
        run.frames(costMs = 22.0, seconds = 0.5)
        assertEquals(40, run.target)
    }

    @Test
    fun aFasterRateIsTakenOnlyWithRoomToSpareForTheWholeHold() {
        val run = Run(SteadyFrameRate(panel120))
        run.frames(costMs = 14.0, seconds = 2.5)
        assertEquals(60, run.target)
        run.frames(costMs = 6.0, seconds = 1.0) // the last 2 s still hold 14 ms frames
        assertEquals(60, run.target)
        run.frames(costMs = 6.0, seconds = 1.5) // 6 ms is under 75% of 8.33 ms
        assertEquals(120, run.target)
        assertEquals(SteadyRateReason.SteppedUp, run.choice?.reason)
    }

    @Test
    fun aStepUpIsOneRateAtATime() {
        val run = Run(SteadyFrameRate(panel120))
        run.frames(costMs = 40.0, seconds = 2.0)
        assertEquals(30, run.target)
        run.frames(costMs = 3.0, seconds = 7.0)
        assertEquals(listOf(30, 40, 60, 120), run.targets)
    }

    @Test
    fun aCostBetweenTheThresholdsMovesNothing() {
        // 7 ms: over 75% of 120's budget (6.25 ms), under 95% of it (7.9 ms).
        val at60 = Run(SteadyFrameRate(panel120)).apply { frames(costMs = 14.0, seconds = 2.5) }
        at60.frames(costMs = 7.0, seconds = 20.0)
        assertEquals(listOf(60), at60.targets)
        val at120 = Run(SteadyFrameRate(panel120)).apply { frames(costMs = 7.0, seconds = 20.0) }
        assertEquals(listOf(120), at120.targets)
    }

    @Test
    fun aCostSwingingAcrossABudgetNeverChangesTheRateTwiceWithinTheHold() {
        val run = Run(SteadyFrameRate(panel120))
        repeat(20) { second -> run.frames(costMs = if (second % 2 == 0) 5.0 else 9.0, seconds = 1.0) }
        val gaps = run.changedAt.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it >= SteadyFrameRate.DEFAULT_HOLD_NANOS }, "changes ${run.changedAt} closer than the hold")
        assertTrue(run.targets.size <= 1 + 20 / 2, "${run.targets}")
    }

    @Test
    fun aFrameWithNoCostIsNotCounted() {
        val selector = SteadyFrameRate(panel120)
        repeat(100) { selector.onFrame(0L, it * ms(16.7)) }
        assertNull(selector.choice)
    }

    @Test
    fun theThresholdsMustLeaveABandBetweenThem() {
        assertFailsWith<IllegalArgumentException> { SteadyFrameRate(panel120, downAt = 0.8, upAt = 0.8) }
        assertFailsWith<IllegalArgumentException> { SteadyFrameRate(listOf(0)) }
    }

    @Test
    fun aFramesCostIsItsWorkLessItsWaitsOrItsGpuTimeWhenLonger() {
        FrameCostReport.submitted(waitedNanos = ms(9.0), gpuNanos = ms(4.0))
        FrameCostReport.submitted(waitedNanos = ms(1.0), gpuNanos = null)
        assertEquals(ms(6.0), FrameCostReport.takeCost(frameNanos = ms(16.0)))
        FrameCostReport.submitted(waitedNanos = ms(12.0), gpuNanos = ms(11.0)) // GPU-bound: the CPU waited on it
        assertEquals(ms(11.0), FrameCostReport.takeCost(frameNanos = ms(16.0)))
        assertEquals(ms(16.0), FrameCostReport.takeCost(frameNanos = ms(16.0))) // nothing reported: all of it
    }

    private fun start(costMs: Double): SteadyRateChoice =
        Run(SteadyFrameRate(panel120)).apply { frames(costMs, seconds = 1.0) }.choice!!

    /** Frames at 60 per second of the clock, each costing the same; records every change of target. */
    private class Run(val selector: SteadyFrameRate) {
        var clock = 0L
        var choice: SteadyRateChoice? = null
        val targets = mutableListOf<Int>()
        val changedAt = mutableListOf<Long>()
        val target: Int? get() = choice?.targetFps

        fun frames(costMs: Double, seconds: Double) {
            repeat((seconds * 60).toInt()) {
                clock += ms(1000.0 / 60)
                choice = selector.onFrame(ms(costMs), clock)
                choice?.targetFps?.takeIf { it != targets.lastOrNull() }?.let {
                    targets += it
                    changedAt += clock
                }
            }
        }
    }

    private companion object {
        fun ms(value: Double): Long = (value * 1_000_000).toLong()
    }
}
