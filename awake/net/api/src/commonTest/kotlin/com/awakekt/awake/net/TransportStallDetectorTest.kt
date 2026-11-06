/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransportStallDetectorTest {
    private val ended = mutableListOf<Pair<StallDirection, Double>>()
    private val detector = TransportStallDetector(onEnded = { direction, seconds -> ended += direction to seconds })

    /** Reads the same [buffered] bytes every quarter second from [from] to [to]. */
    private fun hold(buffered: Long, from: Double, to: Double): TransportStallState {
        var now = from
        var state = detector.sample(buffered, now)
        while (now < to) {
            now += STEP
            state = detector.sample(buffered, now)
        }
        return state
    }

    @Test
    fun rejectsNonPositiveThresholds() {
        assertFailsWith<IllegalArgumentException> { TransportStallDetector(upstreamAfterSeconds = 0.0) }
        assertFailsWith<IllegalArgumentException> { TransportStallDetector(downstreamAfterSeconds = -1.0) }
    }

    @Test
    fun aDrainingBufferNeverStalls() {
        var buffered = 600L
        var now = 0.0
        repeat(40) {
            assertFalse(detector.sample(buffered, now).stalled)
            buffered = if (buffered > 100L) buffered - 50L else 600L
            now += STEP
        }
        assertTrue(ended.isEmpty())
    }

    @Test
    fun anEmptyBufferNeverStalls() {
        assertFalse(hold(buffered = 0L, from = 0.0, to = 5.0).stalled)
    }

    @Test
    fun aBufferThatDoesNotFallForOverASecondStallsUpstream() {
        assertFalse(hold(buffered = 520L, from = 0.0, to = 1.0).upstream)
        val state = detector.sample(520L, 1.25)
        assertTrue(state.upstream)
        assertFalse(state.downstream)
        assertEquals(1.25, state.upstreamSeconds)
        assertEquals(1.25, state.seconds)
    }

    @Test
    fun pingsPilingOntoAStuckBufferStillStall() {
        // The recording: 518 B held, growing by a 2 B ping each second, never falling.
        var now = 0.0
        var buffered = 518L
        while (now <= 3.0) {
            detector.sample(buffered, now)
            if (now % 1.0 == 0.0) buffered += 2L
            now += STEP
        }
        assertTrue(detector.state.upstream)
        assertEquals(3.0, detector.state.upstreamSeconds)
    }

    @Test
    fun aFallingBufferEndsTheStallAndReportsItsLength() {
        hold(buffered = 520L, from = 0.0, to = 3.0)
        assertTrue(detector.state.upstream)
        assertFalse(detector.sample(10L, 3.25).stalled)
        assertEquals(listOf(StallDirection.Upstream to 3.25), ended)
    }

    @Test
    fun aGapBetweenReadingsRestartsTheWaitInsteadOfCallingItAStall() {
        detector.sample(520L, 0.0)
        // A frozen thread: no reading for 5 s, then the same bytes.
        assertFalse(detector.sample(520L, 5.0).stalled)
        assertFalse(hold(buffered = 520L, from = 5.25, to = 6.0).stalled)
        assertTrue(detector.sample(520L, 6.25).upstream)
    }

    @Test
    fun aGapDuringAStallCarriesTheStallOn() {
        hold(buffered = 520L, from = 0.0, to = 2.0)
        val state = detector.sample(520L, 6.0)
        assertTrue(state.upstream)
        assertEquals(6.0, state.upstreamSeconds)
        assertTrue(ended.isEmpty())
    }

    @Test
    fun silenceIsNotAStallUntilInboundIsExpected() {
        assertFalse(hold(buffered = 0L, from = 0.0, to = 10.0).downstream)
    }

    @Test
    fun silenceWhileInboundIsExpectedStallsDownstream() {
        detector.expectInbound = true
        detector.received(0.0)
        assertFalse(hold(buffered = 0L, from = 0.0, to = 2.0).downstream)
        val state = detector.sample(0L, 2.25)
        assertTrue(state.downstream)
        assertFalse(state.upstream)
        assertEquals(2.25, state.downstreamSeconds)
    }

    @Test
    fun steadyPacketsKeepDownstreamClear() {
        detector.expectInbound = true
        var now = 0.0
        repeat(80) {
            detector.received(now)
            assertFalse(detector.sample(0L, now).stalled)
            now += STEP
        }
    }

    @Test
    fun aPacketEndsADownstreamStallAtOnceAndReportsItsLength() {
        detector.expectInbound = true
        detector.received(0.0)
        hold(buffered = 0L, from = 0.0, to = 4.0)
        assertTrue(detector.state.downstream)
        detector.received(4.1)
        assertFalse(detector.state.stalled)
        assertEquals(1, ended.size)
        assertEquals(StallDirection.Downstream, ended.single().first)
        assertEquals(4.1, ended.single().second, 1e-9)
    }

    @Test
    fun expectingInboundStartsTheWaitFromTheNextReading() {
        detector.received(0.0)
        hold(buffered = 0L, from = 0.0, to = 10.0)
        detector.expectInbound = true
        assertFalse(detector.sample(0L, 10.25).downstream)
        assertFalse(hold(buffered = 0L, from = 10.5, to = 12.25).downstream)
        assertTrue(detector.sample(0L, 12.5).downstream)
    }

    @Test
    fun noLongerExpectingInboundClearsADownstreamStallUnreported() {
        detector.expectInbound = true
        hold(buffered = 0L, from = 0.0, to = 3.0)
        assertTrue(detector.state.downstream)
        detector.expectInbound = false
        assertFalse(detector.state.stalled)
        assertTrue(ended.isEmpty())
    }

    @Test
    fun aFrozenThreadDoesNotStallDownstream() {
        detector.expectInbound = true
        detector.received(0.0)
        detector.sample(0L, 0.0)
        // Neither timer nor packets ran for 8 s; packets queued meanwhile have not been handed on yet.
        assertFalse(detector.sample(0L, 8.0).downstream)
    }

    @Test
    fun bothDirectionsCanStallTogether() {
        detector.expectInbound = true
        val state = hold(buffered = 520L, from = 0.0, to = 2.5)
        assertTrue(state.upstream)
        assertTrue(state.downstream)
        assertEquals(2.5, state.seconds)
    }

    @Test
    fun resetForgetsEverything() {
        detector.expectInbound = true
        hold(buffered = 520L, from = 0.0, to = 3.0)
        detector.reset()
        assertEquals(TransportStallState.Clear, detector.state)
        assertFalse(detector.sample(520L, 3.25).stalled)
        assertTrue(ended.isEmpty())
    }

    private companion object {
        const val STEP = 0.25
    }
}
