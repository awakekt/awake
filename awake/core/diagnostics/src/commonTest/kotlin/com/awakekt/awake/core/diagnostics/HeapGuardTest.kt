/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HeapGuardTest {
    @Test
    fun defaultConstantsMatchExpectedThresholds() {
        assertEquals(0.70, HeapGuard.COLLECT_AT)
        assertEquals(0.80, HeapGuard.CLOSE_AT)
        assertEquals(3, HeapGuard.CLOSE_AFTER)
    }

    @Test
    fun invalidConstructorParametersAreRejected() {
        assertFailsWith<IllegalArgumentException> { HeapGuard(collectAt = 0.0) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(collectAt = -0.1) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(collectAt = 1.1) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(collectAt = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(collectAt = 0.80, closeAt = 0.70) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(closeAt = 1.05) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(closeAt = Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(closeAfter = 0) }
        assertFailsWith<IllegalArgumentException> { HeapGuard(closeAfter = -2) }
    }

    @Test
    fun belowCollectThresholdReturnsNoneAndSkipsCollectCallback() {
        val guard = HeapGuard()
        var collectCalls = 0

        val action = guard.check(used = 699L, limit = 1_000L) {
            collectCalls++
            500L
        }

        assertEquals(HeapGuard.Action.None, action)
        assertEquals(0, collectCalls)
        assertEquals(0, guard.consecutiveBreaches)
    }

    @Test
    fun invalidUsageOrLimitReturnsNoneAndResetsBreachStreak() {
        val guard = HeapGuard()
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 850L, limit = 1_000L))
        assertEquals(1, guard.consecutiveBreaches)

        assertEquals(HeapGuard.Action.None, guard.check(used = 850L, limit = 0L))
        assertEquals(0, guard.consecutiveBreaches)

        assertEquals(HeapGuard.Action.Collect, guard.check(used = 850L, limit = 1_000L))
        assertEquals(1, guard.consecutiveBreaches)

        assertEquals(HeapGuard.Action.None, guard.check(used = -1L, limit = 1_000L))
        assertEquals(0, guard.consecutiveBreaches)
    }

    @Test
    fun betweenCollectAndCloseThresholdsAlwaysReturnsCollectWithoutEscalating() {
        val guard = HeapGuard()
        repeat(10) {
            assertEquals(HeapGuard.Action.Collect, guard.check(used = 750L, limit = 1_000L))
            assertEquals(0, guard.consecutiveBreaches)
        }
    }

    @Test
    fun sustainedBreachesEscalateToCloseAndResetOnRecovery() {
        val guard = HeapGuard()

        assertEquals(HeapGuard.Action.Collect, guard.check(used = 800L, limit = 1_000L))
        assertEquals(1, guard.consecutiveBreaches)

        assertEquals(HeapGuard.Action.Collect, guard.check(used = 850L, limit = 1_000L))
        assertEquals(2, guard.consecutiveBreaches)

        assertEquals(HeapGuard.Action.Close, guard.check(used = 900L, limit = 1_000L))
        assertEquals(3, guard.consecutiveBreaches)

        // Remains Close while sustained.
        assertEquals(HeapGuard.Action.Close, guard.check(used = 920L, limit = 1_000L))
        assertEquals(4, guard.consecutiveBreaches)

        // Dropping below closeAt (even still in Collect band) resets streak.
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 750L, limit = 1_000L))
        assertEquals(0, guard.consecutiveBreaches)

        // Needs 3 fresh consecutive breaches again.
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 820L, limit = 1_000L))
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 820L, limit = 1_000L))
        assertEquals(HeapGuard.Action.Close, guard.check(used = 820L, limit = 1_000L))
    }

    @Test
    fun explicitResetClearsConsecutiveBreaches() {
        val guard = HeapGuard()
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 900L, limit = 1_000L))
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 900L, limit = 1_000L))
        assertEquals(2, guard.consecutiveBreaches)

        guard.reset()
        assertEquals(0, guard.consecutiveBreaches)

        assertEquals(HeapGuard.Action.Collect, guard.check(used = 900L, limit = 1_000L))
        assertEquals(1, guard.consecutiveBreaches)
    }

    @Test
    fun collectCallbackReEvaluatesLiveUsageAgainstCloseThreshold() {
        val guard = HeapGuard()
        var collectCalls = 0

        // Uncollected garbage above 80% drops to 50% after collection: never escalates to Close.
        repeat(5) {
            val action = guard.check(used = 900L, limit = 1_000L) {
                collectCalls++
                500L
            }
            assertEquals(HeapGuard.Action.Collect, action)
            assertEquals(0, guard.consecutiveBreaches)
        }
        assertEquals(5, collectCalls)

        // Genuine leak stays at 85% even after collection: escalates after closeAfter checks.
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 900L, limit = 1_000L) { 850L })
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 900L, limit = 1_000L) { 850L })
        assertEquals(HeapGuard.Action.Close, guard.check(used = 900L, limit = 1_000L) { 850L })
    }

    @Test
    fun customCloseAfterOneClosesImmediately() {
        val guard = HeapGuard(collectAt = 0.50, closeAt = 0.60, closeAfter = 1)
        assertEquals(HeapGuard.Action.None, guard.check(used = 499L, limit = 1_000L))
        assertEquals(HeapGuard.Action.Collect, guard.check(used = 550L, limit = 1_000L))
        assertEquals(HeapGuard.Action.Close, guard.check(used = 600L, limit = 1_000L))
    }
}
