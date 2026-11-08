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

class PingLimitTest {
    @Test
    fun burstAnswersBunchedPingsAndRefillsAtSustainedRate() {
        var now = 0L
        val limit = PingLimit(clock = { now })

        val burstAllowed = (0 until 20).count { limit.allow() }
        assertEquals(PingLimit.DEFAULT_BURST.toInt(), burstAllowed)
        assertFalse(limit.allow())

        // After 500 ms at 2.0/s, exactly one reply refills.
        now += 500_000_000L
        assertTrue(limit.allow())
        assertFalse(limit.allow())

        // Explicit timestamp overload refills up to burst cap and clamps non-monotonic steps.
        assertFalse(limit.allow(now - 100_000_000L))
        now += 10_000_000_000L
        assertEquals(PingLimit.DEFAULT_BURST.toInt(), (0 until 20).count { limit.allow(now) })
    }

    @Test
    fun rejectsInvalidRateOrBurstParameters() {
        assertFailsWith<IllegalArgumentException> { PingLimit({ 0L }, perSecond = 0.0) }
        assertFailsWith<IllegalArgumentException> { PingLimit({ 0L }, burst = 0.5) }
    }
}
