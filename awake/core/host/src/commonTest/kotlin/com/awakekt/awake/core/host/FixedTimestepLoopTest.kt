/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixedTimestepLoopTest {
    private val fixedDelta = 1f / 60f

    @Test
    fun rendersExactlyOncePerAdvanceCallRegardlessOfStepCount() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta, maxStepsPerFrame = 5)
        var renderCalls = 0

        loop.advance(frameDelta = fixedDelta * 2.5f, fixedUpdate = {}, render = { renderCalls++ })

        assertEquals(1, renderCalls)
    }

    @Test
    fun accumulatesPartialFrameDeltaAcrossCalls() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta, maxStepsPerFrame = 5)
        var steps = 0

        // Two half-steps should combine into exactly one fixed update, not zero or two.
        loop.advance(frameDelta = fixedDelta * 0.5f, fixedUpdate = { steps++ }, render = {})
        assertEquals(0, steps)
        loop.advance(frameDelta = fixedDelta * 0.5f, fixedUpdate = { steps++ }, render = {})
        assertEquals(1, steps)
    }

    @Test
    fun runsMultipleFixedStepsWhenFrameDeltaIsLarge() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta, maxStepsPerFrame = 5)
        var steps = 0

        loop.advance(frameDelta = fixedDelta * 3.2f, fixedUpdate = { steps++ }, render = {})

        assertEquals(3, steps)
    }

    @Test
    fun everyFixedUpdateCallReceivesExactlyFixedDelta() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta, maxStepsPerFrame = 5)
        val deltasSeen = mutableListOf<Float>()

        loop.advance(frameDelta = fixedDelta * 3f, fixedUpdate = { deltasSeen += it }, render = {})

        assertEquals(listOf(fixedDelta, fixedDelta, fixedDelta), deltasSeen)
    }

    @Test
    fun cannotExceedMaxStepsPerFrameAndDropsTheRemainder() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta, maxStepsPerFrame = 3)
        var steps = 0

        // Ten pending steps' worth of delta in one frame -- without the cap this would run
        // 10 fixed updates in a single advance() call.
        loop.advance(frameDelta = fixedDelta * 10f, fixedUpdate = { steps++ }, render = {})

        assertEquals(3, steps)
    }

    @Test
    fun droppedRemainderDoesNotCarryOverToTheNextFrame() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta, maxStepsPerFrame = 3)
        var steps = 0

        loop.advance(frameDelta = fixedDelta * 10f, fixedUpdate = { steps++ }, render = {})
        assertEquals(3, steps)

        // If the leftover 7 steps' worth of time had carried over, this call alone would
        // immediately hit the cap again with zero new frameDelta.
        loop.advance(frameDelta = 0f, fixedUpdate = { steps++ }, render = {})
        assertEquals(3, steps)
    }

    @Test
    fun renderAlphaReflectsFractionalProgressTowardTheNextStep() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta, maxStepsPerFrame = 5)
        var alpha = -1f

        loop.advance(frameDelta = fixedDelta * 0.25f, fixedUpdate = {}, render = { alpha = it })

        assertTrue(alpha in 0f..1f)
        assertTrue(kotlin.math.abs(0.25f - alpha) < 1e-4f, "expected ~0.25, got $alpha")
    }

    @Test
    fun theDefaultCapIsFiveStepsAndAFrameWithinItDropsNothing() {
        val loop = FixedTimestepLoop()
        var steps = 0
        var heard = 0
        loop.addTimeDroppedListener { _, _ -> heard++ }

        loop.advance(frameDelta = fixedDelta * 4.5f, fixedUpdate = { steps++ }, render = {})

        assertEquals(5, loop.maxStepsPerFrame)
        assertEquals(fixedDelta, loop.fixedDelta)
        assertEquals(4, steps)
        assertEquals(4, loop.stepsThisFrame)
        assertEquals(0f, loop.droppedSeconds)
        assertEquals(0, loop.droppedSteps)
        assertEquals(0L, loop.droppedStepsTotal)
        assertEquals(0, heard, "a frame within the cap drops no time")
    }

    @Test
    fun longFramesAtSixtyStepsASecondReportExactlyWhatTheyDropped() {
        // 100 ms is six steps, 267 ms sixteen and a fiftieth, a second sixty: five run, the rest is dropped.
        assertDrops(rate = 60, frameSeconds = 0.1f, cap = 5, droppedSteps = 1)
        assertDrops(rate = 60, frameSeconds = 0.267f, cap = 5, droppedSteps = 11)
        assertDrops(rate = 60, frameSeconds = 1f, cap = 5, droppedSteps = 55)
    }

    @Test
    fun longFramesAtACustomRateReportExactlyWhatTheyDropped() {
        // At 120 steps a second, 100 ms is twelve steps, 267 ms thirty-two and a twenty-fifth, a second 120.
        assertDrops(rate = 120, frameSeconds = 0.1f, cap = 5, droppedSteps = 7)
        assertDrops(rate = 120, frameSeconds = 0.267f, cap = 5, droppedSteps = 27)
        assertDrops(rate = 120, frameSeconds = 1f, cap = 5, droppedSteps = 115)
        assertDrops(rate = 120, frameSeconds = 1f, cap = 15, droppedSteps = 105)
    }

    @Test
    fun aHigherCapRunsMoreStepsAndDropsLess() {
        assertDrops(rate = 60, frameSeconds = 0.267f, cap = 15, droppedSteps = 1)
        assertDrops(rate = 60, frameSeconds = 1f, cap = 15, droppedSteps = 45)
        // 100 ms is six steps: a cap of 15 runs them all and drops nothing.
        assertDrops(rate = 60, frameSeconds = 0.1f, cap = 15, droppedSteps = 0, ran = 6)
    }

    @Test
    fun theCapCanBeRaisedBetweenFrames() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta)
        var steps = 0

        loop.advance(frameDelta = 1f, fixedUpdate = { steps++ }, render = {})
        assertEquals(5, steps)

        loop.maxStepsPerFrame = 15
        loop.advance(frameDelta = 1f, fixedUpdate = { steps++ }, render = {})
        assertEquals(20, steps)
        assertEquals(15, loop.stepsThisFrame)
        assertEquals(45, loop.droppedSteps)
        assertEquals(55L + 45L, loop.droppedStepsTotal)
    }

    @Test
    fun theListenerHearsOnceForEachFrameThatDropsTimeWithWhatTheReportSays() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta)
        val heard = mutableListOf<Pair<Float, Int>>()
        loop.addTimeDroppedListener { seconds, steps -> heard += seconds to steps }

        loop.advance(frameDelta = 0.1f, fixedUpdate = {}, render = {})
        loop.advance(frameDelta = fixedDelta, fixedUpdate = {}, render = {})
        loop.advance(frameDelta = 1f, fixedUpdate = {}, render = {})

        assertEquals(2, heard.size, "the frame within the cap is not heard: $heard")
        assertClose(0.1f - 5 * fixedDelta, heard[0].first)
        assertEquals(1, heard[0].second)
        assertClose(1f - 5 * fixedDelta, heard[1].first)
        assertEquals(55, heard[1].second)
        assertEquals(loop.droppedSeconds, heard[1].first)
        assertEquals(56L, loop.droppedStepsTotal)
    }

    @Test
    fun theReportDescribesTheFrameBeforeItRenders() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta)
        var heardFirst = false
        var seenAtRender = -1
        loop.addTimeDroppedListener { _, _ -> heardFirst = seenAtRender == -1 }

        loop.advance(frameDelta = 1f, fixedUpdate = {}, render = { seenAtRender = loop.droppedSteps })

        assertTrue(heardFirst, "the listener runs before render")
        assertEquals(55, seenAtRender)
    }

    @Test
    fun aRemovedListenerIsNotCalledAndOneAddedTwiceIsCalledOnce() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta)
        var calls = 0
        val listener = TimeDroppedListener { _, _ -> calls++ }
        loop.addTimeDroppedListener(listener)
        loop.addTimeDroppedListener(listener)

        loop.advance(frameDelta = 1f, fixedUpdate = {}, render = {})
        assertEquals(1, calls)

        loop.removeTimeDroppedListener(listener)
        loop.advance(frameDelta = 1f, fixedUpdate = {}, render = {})
        assertEquals(1, calls)
    }

    @Test
    fun aListenerRemovingItselfStillLetsTheOthersHearThatFrame() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta)
        var other = 0
        lateinit var once: TimeDroppedListener
        once = TimeDroppedListener { _, _ -> loop.removeTimeDroppedListener(once) }
        loop.addTimeDroppedListener(once)
        loop.addTimeDroppedListener { _, _ -> other++ }

        loop.advance(frameDelta = 1f, fixedUpdate = {}, render = {})
        loop.advance(frameDelta = 1f, fixedUpdate = {}, render = {})

        assertEquals(2, other)
    }

    @Test
    fun aFrameAfterADroppingOneReportsNothingDropped() {
        val loop = FixedTimestepLoop(fixedDelta = fixedDelta)

        loop.advance(frameDelta = 1f, fixedUpdate = {}, render = {})
        loop.advance(frameDelta = fixedDelta * 2f, fixedUpdate = {}, render = {})

        assertEquals(2, loop.stepsThisFrame)
        assertEquals(0f, loop.droppedSeconds)
        assertEquals(0, loop.droppedSteps)
        assertEquals(55L, loop.droppedStepsTotal, "the total keeps what earlier frames dropped")
    }

    @Test
    fun aCapBelowOneIsRefused() {
        assertFailsWith<IllegalArgumentException> { FixedTimestepLoop(maxStepsPerFrame = 0) }
        assertFailsWith<IllegalArgumentException> { FixedTimestepLoop().maxStepsPerFrame = 0 }
    }

    /**
     * One frame of [frameSeconds] at [rate] steps a second with a cap of [cap]: it runs [ran] steps
     * and reports [droppedSteps] whole steps, and the seconds past the steps it ran, as dropped.
     */
    private fun assertDrops(rate: Int, frameSeconds: Float, cap: Int, droppedSteps: Int, ran: Int = cap) {
        val step = 1f / rate
        val loop = FixedTimestepLoop(fixedDelta = step, maxStepsPerFrame = cap)
        var steps = 0
        var heard = 0

        loop.addTimeDroppedListener { _, _ -> heard++ }
        loop.advance(frameDelta = frameSeconds, fixedUpdate = { steps++ }, render = {})

        val label = "$frameSeconds s at $rate Hz, cap $cap"
        assertEquals(ran, steps, label)
        assertEquals(ran, loop.stepsThisFrame, label)
        assertEquals(droppedSteps, loop.droppedSteps, label)
        assertEquals(droppedSteps.toLong(), loop.droppedStepsTotal, label)
        val dropped = if (ran == cap) frameSeconds - cap * step else 0f
        assertClose(dropped, loop.droppedSeconds, label)
        assertEquals(if (dropped > 0f) 1 else 0, heard, label)
    }

    private fun assertClose(expected: Float, actual: Float, label: String = "") {
        assertTrue(abs(expected - actual) < 1e-5f, "$label: expected $expected, got $actual")
    }
}
