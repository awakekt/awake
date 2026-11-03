/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredictionHistoryTest {
    private val out = Vec3f(0f, 0f, 0f)

    @Test
    fun keepsEachInputsPose() {
        val history = PredictionHistory(capacity = 8)
        history.record(1, 1f, 2f, 3f, timeSeconds = 0.5)
        history.record(2, Vec3f(4f, 5f, 6f), timeSeconds = 0.6)
        assertTrue(history.poseAt(2, out))
        assertEquals(Vec3f(4f, 5f, 6f), out)
        assertTrue(1L in history)
        assertFalse(history.poseAt(3, out), "never recorded")
        assertEquals(Vec3f(4f, 5f, 6f), out, "left as it was")
        assertEquals(2L, history.newestSeq)
    }

    @Test
    fun theRingOverwritesTheOldestAsItWraps() {
        val history = PredictionHistory(capacity = 4)
        for (seq in 1L..6L) history.record(seq, seq.toFloat(), 0f, 0f)
        assertFalse(1L in history)
        assertFalse(2L in history)
        for (seq in 3L..6L) {
            assertTrue(history.poseAt(seq, out), "input $seq kept")
            assertEquals(seq.toFloat(), out.x)
        }
        assertFalse(history.poseAt(2, out), "slot 2 holds input 6 now, not 2")
    }

    @Test
    fun shiftMovesEveryKeptPose() {
        val history = PredictionHistory(capacity = 4)
        for (seq in 1L..6L) history.record(seq, seq.toFloat(), 0f, 0f)
        history.shift(0.5f, -1f, 2f)
        for (seq in 3L..6L) {
            history.poseAt(seq, out)
            assertEquals(Vec3f(seq + 0.5f, -1f, 2f), out)
        }
    }

    @Test
    fun theOldestUnacknowledgedInputAndItsAge() {
        val history = PredictionHistory(capacity = 4)
        for (seq in 1L..6L) history.record(seq, 0f, 0f, 0f, timeSeconds = seq * 0.1)
        assertEquals(3L, history.oldestUnacked(ackSeq = 0), "the oldest kept, past the ack")
        assertEquals(5L, history.oldestUnacked(ackSeq = 4))
        assertEquals(-1L, history.oldestUnacked(ackSeq = 6))
        assertEquals(0.5, history.oldestUnackedTime(ackSeq = 4), 1e-9)
        assertTrue(history.oldestUnackedTime(ackSeq = 6).isNaN())
        assertEquals(0.5, history.secondsSinceOldestUnacked(ackSeq = 4, nowSeconds = 1.0), 1e-9)
        assertEquals(0.0, history.secondsSinceOldestUnacked(ackSeq = 6, nowSeconds = 1.0))
        assertEquals(Double.POSITIVE_INFINITY, history.secondsSinceOldestUnacked(ackSeq = 0, nowSeconds = 1.0), "input 1 was overwritten")
    }

    @Test
    fun clearForgetsEverything() {
        val history = PredictionHistory(capacity = 4)
        history.record(1, 1f, 1f, 1f)
        history.clear()
        assertFalse(1L in history)
        assertEquals(-1L, history.newestSeq)
        assertEquals(-1L, history.oldestUnacked(0))
    }

    @Test
    fun refusesWhatCannotBeKept() {
        assertFailsWith<IllegalArgumentException> { PredictionHistory(capacity = 0) }
        assertFailsWith<IllegalArgumentException> { PredictionHistory().record(-1, 0f, 0f, 0f) }
        assertFalse(-1L in PredictionHistory())
    }
}
