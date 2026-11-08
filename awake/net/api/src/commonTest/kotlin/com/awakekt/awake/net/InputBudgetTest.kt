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

class InputBudgetTest {
    private fun atMillis(millis: Long): Long = millis * 1_000_000L

    @Test
    fun steadyClientNeverExhaustsBudgetAndShortStallArrivesWithinBurst() {
        val budget = InputBudget(perSecond = 45.0, burst = 15.0)
        assertTrue((0 until 300).all { budget.take(atMillis(it * 1_000L / 30)) })

        val stalled = InputBudget(perSecond = 45.0, burst = 15.0)
        (0 until 30).forEach { stalled.take(atMillis(it * 1_000L / 30)) }
        assertTrue((0 until 9).all { stalled.take(atMillis(1_300L)) })
    }

    @Test
    fun floodRunsBudgetDryAndElapsedTimeRefillsTokens() {
        val budget = InputBudget(perSecond = 45.0, burst = 15.0)
        val initial = (0 until 40).count { budget.take(atMillis(0L)) }
        assertEquals(15, initial)
        assertFalse(budget.take(atMillis(10L)))
        assertTrue(budget.take(atMillis(40L)))
        assertEquals(15, (0 until 40).count { budget.take(atMillis(1_040L)) })
    }

    @Test
    fun stallGapCreditAcceptsStrictlyAdvancingSeqAndRejectsUnnumberedOrRepeatedFloods() {
        val budget = InputBudget(perSecond = 30.0, burst = 10.0, stallSeconds = 1.0)
        var seq = 1L
        repeat(10) { assertTrue(budget.take(atMillis(0L), seq = seq++)) }
        assertFalse(budget.take(atMillis(0L), seq = seq))

        // After 1.5 s silence: 10 burst tokens + 30 gap-credit tokens (capped at 1.0 s * 30/s).
        var acceptedWithSeq = 0
        while (budget.take(atMillis(1_500L), seq = seq)) {
            acceptedWithSeq++
            seq++
        }
        assertEquals(40, acceptedWithSeq)

        // Repeated/unnumbered inputs get no gap credit beyond normal burst.
        val unnumbered = InputBudget(perSecond = 30.0, burst = 10.0, stallSeconds = 1.0)
        repeat(10) { assertTrue(unnumbered.take(atMillis(0L), seq = 0L)) }
        assertEquals(10, (0 until 40).count { unnumbered.take(atMillis(1_500L), seq = 0L) })

        budget.resetSeq()
        assertTrue(budget.take(atMillis(2_500L), seq = 1L))
    }

    @Test
    fun serverTickLagWidensBurstForSteadyClientsWhileCatchingRealFlooders() {
        val longStalls = { tick: Int -> if (tick == 60 || tick == 200) 1_200L else 5L }
        assertEquals(0, simulateFloodedTicks(clientPerSecond = 30, workMillis = longStalls, lagged = true))
        assertTrue(simulateFloodedTicks(clientPerSecond = 30, workMillis = longStalls, lagged = false) > 0)

        val overloaded = { tick: Int -> if (tick % 40 == 39) 700L else 50L }
        assertEquals(0, simulateFloodedTicks(clientPerSecond = 30, workMillis = overloaded, seconds = 20))

        val slow = { tick: Int -> if (tick % 40 == 39) 450L else 100L }
        assertTrue(simulateFloodedTicks(clientPerSecond = 300, workMillis = slow, seconds = 5) >= 5)
    }

    @Test
    fun silentClientDuringOnTimeTicksGetsNoExtraAllowanceAndValidationRejectsBadArgs() {
        assertFailsWith<IllegalArgumentException> { InputBudget(0.0, 10.0) }
        assertFailsWith<IllegalArgumentException> { InputBudget(30.0, 0.5) }
        assertFailsWith<IllegalArgumentException> { InputBudget(30.0, 10.0, -1.0) }

        val budget = InputBudget(perSecond = 45.0, burst = 15.0)
        val lag = TickLag(ticksPerSecond = 30, window = 30)
        (0..300).forEach { lag.ticked(atMillis(it * 1_000L / 30)) }
        assertEquals(15, (0 until 100).count { budget.take(atMillis(10_000L), lag.lagNanos) })
    }

    private fun simulateFloodedTicks(
        clientPerSecond: Int,
        workMillis: (Int) -> Long,
        seconds: Int = 10,
        lagged: Boolean = true,
    ): Int {
        val budget = InputBudget(perSecond = 45.0, burst = 15.0)
        val lag = TickLag(ticksPerSecond = 30, window = 30)
        val period = 1_000_000_000L / 30
        val every = 1_000_000_000L / clientPerSecond
        var began = 0L
        var sent = 0L
        var floodedTicks = 0
        var tick = 0
        while (began < seconds * 1_000_000_000L) {
            lag.ticked(began)
            var refused = false
            while (sent <= began) {
                if (!budget.take(began, if (lagged) lag.lagNanos else 0L)) refused = true
                sent += every
            }
            if (refused) floodedTicks++
            began += maxOf(period, atMillis(workMillis(tick)))
            tick++
        }
        return floodedTicks
    }
}

class TickLagTest {
    private fun atMillis(millis: Long): Long = millis * 1_000_000L

    @Test
    fun onTimeTicksProduceZeroLagAndNominalAllowance() {
        val lag = TickLag(ticksPerSecond = 10, window = 5)
        (0..20).forEach { lag.ticked(atMillis(it * 100L)) }
        assertEquals(0L, lag.lagNanos)
        assertEquals(0L, lag.longestNanos)
        assertEquals(4, lag.allowance(4))
        assertEquals(1, lag.allowance(1))
    }

    @Test
    fun lateTickRemainsInWindowUntilEvicted() {
        val lag = TickLag(ticksPerSecond = 10, window = 3)
        lag.ticked(atMillis(0L))
        lag.ticked(atMillis(100L))
        lag.ticked(atMillis(1_100L))
        assertEquals(atMillis(900L), lag.lagNanos)
        assertEquals(4 + 36, lag.allowance(4))

        lag.ticked(atMillis(1_200L))
        lag.ticked(atMillis(1_300L))
        assertEquals(atMillis(900L), lag.lagNanos)
        assertEquals(4 + 36, lag.allowance(4))

        lag.ticked(atMillis(1_400L))
        assertEquals(0L, lag.lagNanos)
        assertEquals(4, lag.allowance(4))
    }

    @Test
    fun mildConsistentLatenessScalesAllowanceByWorstSingleTickNotWindowSum() {
        assertFailsWith<IllegalArgumentException> { TickLag(0, 5) }
        assertFailsWith<IllegalArgumentException> { TickLag(30, 0) }

        val lag = TickLag(ticksPerSecond = 30, window = 30)
        val period = 1_000_000_000L / 30
        (0..60).forEach { lag.ticked(it * (period + atMillis(10L))) }
        assertEquals(atMillis(300L), lag.lagNanos)
        assertEquals(4 + 1, lag.allowance(4))
        assertEquals(1, lag.allowance(1))
        assertFailsWith<IllegalArgumentException> { lag.allowance(-1) }
    }
}
