/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ViolationWindowTest {
    @Test
    fun rejectsNegativeDecayTicks() {
        assertFailsWith<IllegalArgumentException> {
            ViolationWindow(decayTicks = -1L)
        }
    }

    @Test
    fun zeroDecayAccumulatesUntilReset() {
        val window = ViolationWindow(decayTicks = 0L)
        assertEquals(0, window.count)
        assertEquals(-1L, window.lastViolationTick)

        assertEquals(1, window.record(atTick = 10L))
        assertEquals(2, window.record(atTick = 1_000L))
        assertEquals(2, window.countAt(atTick = 50_000L))
        assertEquals(1_000L, window.lastViolationTick)

        window.reset()
        assertEquals(0, window.count)
        assertEquals(-1L, window.lastViolationTick)
        assertEquals(1, window.record(atTick = 0L))
        assertEquals(0L, window.lastViolationTick)
    }

    @Test
    fun quietTicksDecayCountWhileSubWindowBurstsAccumulate() {
        val window = ViolationWindow(decayTicks = 10L)
        assertEquals(1, window.record(atTick = 0L))
        assertEquals(2, window.record(atTick = 5L), "5 ticks is shorter than decayTicks=10")
        assertEquals(3, window.record(atTick = 12L), "7 ticks since tick 5 still sheds nothing")
        assertEquals(12L, window.lastViolationTick)

        // Pure queries do not mutate stored state.
        assertEquals(3, window.countAt(atTick = 21L))
        assertEquals(2, window.countAt(atTick = 22L))
        assertEquals(1, window.countAt(atTick = 37L))
        assertEquals(0, window.countAt(atTick = 50L))
        assertEquals(3, window.count)
        assertEquals(12L, window.lastViolationTick)

        // Recording after 25 quiet ticks sheds 2 violations (3 -> 1) then adds 1 -> 2.
        assertEquals(2, window.record(atTick = 37L))
        assertEquals(37L, window.lastViolationTick)

        // Recording after a long quiet span decays to 0 before counting the new violation.
        assertEquals(1, window.record(atTick = 200L))
        assertEquals(200L, window.lastViolationTick)
    }

    @Test
    fun equalOrOutOfOrderTicksNeverProduceNegativeDecayOrMoveTickBackwards() {
        val window = ViolationWindow(decayTicks = 10L)
        assertEquals(1, window.record(atTick = 100L))
        assertEquals(2, window.record(atTick = 100L))
        assertEquals(100L, window.lastViolationTick)

        assertEquals(2, window.countAt(atTick = 50L))
        assertEquals(3, window.record(atTick = 50L))
        assertEquals(100L, window.lastViolationTick, "lastViolationTick must remain monotonic")
    }

    @Test
    fun hugeTickGapsDecayToZeroWithoutOverflow() {
        val window = ViolationWindow(decayTicks = 1L)
        window.record(atTick = Long.MIN_VALUE)
        window.record(atTick = Long.MIN_VALUE)
        assertEquals(0, window.countAt(atTick = Long.MAX_VALUE))
        assertEquals(1, window.record(atTick = Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, window.lastViolationTick)
    }
}
