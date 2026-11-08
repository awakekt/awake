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
import kotlin.test.assertTrue

class TickScheduleTest {
    @Test
    fun tracksOnTimeCatchUpAndBacklogDropDeadlines() {
        val schedule = TickSchedule(ticksPerSecond = 20, maxBacklogTicks = 3, startNanos = 1_000L)
        assertEquals(50_000_000L, schedule.periodNanos)
        assertFalse(schedule.due(999L))
        assertEquals(0L, schedule.owed(999L))

        assertTrue(schedule.due(1_000L))
        assertEquals(1L, schedule.owed(1_000L))
        assertEquals(0L, schedule.begin(1_000L))
        assertEquals(50_001_000L, schedule.nextAt)

        // Late by 2 periods (within maxBacklogTicks = 3): 0 dropped, 2 catch-up ticks owed after first begin.
        val twoPeriodsLate = 50_001_000L + 2L * 50_000_000L
        assertEquals(2L, schedule.behind(twoPeriodsLate))
        assertEquals(3L, schedule.owed(twoPeriodsLate))
        assertEquals(0L, schedule.begin(twoPeriodsLate))
        assertEquals(2L, schedule.owed(twoPeriodsLate))
        assertEquals(0L, schedule.begin(twoPeriodsLate))
        assertEquals(0L, schedule.begin(twoPeriodsLate))
        assertFalse(schedule.due(twoPeriodsLate))

        // Late by 7 additional periods past nextAt (maxBacklogTicks = 3): drops 4, leaves 3 catch-up ticks.
        val stalled = schedule.nextAt + 7L * 50_000_000L
        assertEquals(7L, schedule.behind(stalled))
        assertEquals(4L, schedule.begin(stalled))
        assertEquals(3L, schedule.owed(stalled))
    }

    @Test
    fun validatesConstructorArguments() {
        assertFailsWith<IllegalArgumentException> { TickSchedule(0, 3, 0L) }
        assertFailsWith<IllegalArgumentException> { TickSchedule(30, -1, 0L) }
    }
}
