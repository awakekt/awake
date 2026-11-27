/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputCadenceTest {
    @Test
    fun fixedStepWindowsAndDebtBalancedRestRealignmentHoldExactOneToOneSendRate() {
        val cadence = InputCadence()
        cadence.ensureStepsPerInput(1f / 60f, serverTicksPerSecond = 30)
        assertEquals(2, cadence.stepsPerInput)

        // Start an idle window on step 0.
        cadence.onLatched(repayAllowed = true)
        assertTrue(cadence.open)
        assertFalse(cadence.onStepCompleted()) // step 1 of 2

        // Movement begins mid-window on step 1: close idle early, borrowing 1 step of debt.
        assertTrue(cadence.tryRealignFromRest(canRealign = true))
        assertEquals(1, cadence.debtSteps)
        assertFalse(cadence.open)

        // Moving input runs full 2 steps without repaying debt.
        cadence.onLatched(repayAllowed = false)
        assertEquals(2, cadence.targetSteps)
        assertFalse(cadence.onStepCompleted())
        assertTrue(cadence.onStepCompleted())

        // Subsequent stationary input repays the 1 borrowed step (runs 3 steps total).
        cadence.onLatched(repayAllowed = true)
        assertEquals(0, cadence.debtSteps)
        assertEquals(3, cadence.targetSteps)
        assertFalse(cadence.onStepCompleted())
        assertFalse(cadence.onStepCompleted())
        assertTrue(cadence.onStepCompleted())
    }

    @Test
    fun rapidOddRestReversalsNeverAccumulateUnboundedSendDrift() {
        val cadence = InputCadence()
        cadence.ensureStepsPerInput(1f / 60f, serverTicksPerSecond = 30)
        var sent = 0L
        var totalSteps = 0
        val rests = intArrayOf(1, 3, 1, 5, 1, 2, 1, 3, 1, 4, 1, 1, 3, 2, 1, 5)
        for (rest in rests) {
            repeat(8) {
                if (cadence.tryRealignFromRest(canRealign = true)) Unit
                if (!cadence.open) {
                    sent++
                    cadence.onLatched(repayAllowed = false)
                }
                cadence.onStepCompleted()
                totalSteps++
            }
            repeat(rest) {
                if (!cadence.open) {
                    sent++
                    cadence.onLatched(repayAllowed = true)
                }
                cadence.onStepCompleted()
                totalSteps++
            }
        }
        // Finish settling on rest.
        repeat(10) {
            if (!cadence.open) {
                sent++
                cadence.onLatched(repayAllowed = true)
            }
            cadence.onStepCompleted()
            totalSteps++
        }
        val expected = totalSteps / 2L
        assertTrue(sent in expected..(expected + 1L), "send rate stayed locked to 1/tick: sent=$sent expected=$expected")
    }

    @Test
    fun ackAheadAndDroppedStepsRoundingBehaveAccurately() {
        val cadence = InputCadence()
        cadence.ensureStepsPerInput(1f / 60f, serverTicksPerSecond = 30)
        cadence.onLatched(repayAllowed = false)
        assertTrue(cadence.isAckAhead(ackSeq = 5L, inputSeq = 5L))
        assertEquals(4L, cadence.lastCompletedSeq(5L))
        cadence.onStepCompleted()
        cadence.onStepCompleted()
        assertFalse(cadence.isAckAhead(ackSeq = 5L, inputSeq = 5L))
        assertEquals(5L, cadence.lastCompletedSeq(5L))

        val step = 1f / 60f
        assertEquals(7, cadence.droppedSteps(0.2f, stepsRun = 5, stepSeconds = step, maxStepsPerFrame = 5, reported = 7))
        for (total in 6..15) {
            var delta = 0f
            repeat(total) { delta += step }
            assertEquals(
                total - 5,
                cadence.droppedSteps(delta, stepsRun = 5, stepSeconds = step, maxStepsPerFrame = 5, reported = null),
                "total=$total must not truncate one step short",
            )
        }

        cadence.reset()
        assertEquals(0, cadence.stepsPerInput)
        assertEquals(0, cadence.debtSteps)
        assertFalse(cadence.open)
    }
}
