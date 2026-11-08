/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.server.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TickHealthTest {
    private val clock = ManualTickClock(start = 0L)
    private val health = TickHealth(
        stalledAfterNanos = 5_000L,
        failuresBeforeUnhealthy = 3,
        clock = clock,
    )

    @Test
    fun reportsUnhealthyBeforeFirstTickOnStallOnConsecutiveFailuresAndAfterEnd() {
        assertFalse(health.healthy())
        assertEquals(TickHealth.REASON_NOT_STARTED, health.unhealthyReason())

        health.ticked(failed = false)
        assertTrue(health.healthy())
        assertNull(health.unhealthyReason())

        clock.advanceTo(4_999L)
        assertTrue(health.healthy())
        clock.advanceTo(5_000L)
        assertEquals(TickHealth.REASON_STALLED, health.unhealthyReason())

        health.ticked(failed = false)
        assertTrue(health.healthy())

        repeat(2) { health.ticked(failed = true) }
        assertTrue(health.healthy())
        health.ticked(failed = true)
        assertEquals("the last 3 ticks failed", health.unhealthyReason())

        health.ticked(failed = false)
        assertTrue(health.healthy())

        health.ended()
        assertEquals(TickHealth.REASON_STOPPED, health.unhealthyReason())
    }

    @Test
    fun validatesConstructorArguments() {
        assertFailsWith<IllegalArgumentException> { TickHealth(0L, 3, clock) }
        assertFailsWith<IllegalArgumentException> { TickHealth(1_000L, 0, clock) }
    }
}
