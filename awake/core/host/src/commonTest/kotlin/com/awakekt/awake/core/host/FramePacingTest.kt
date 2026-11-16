/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The pacing arithmetic and the pacer each mode gets (#1699). */
class FramePacingTest {
    private val hz60 = 16_666_667L
    private val hz90 = 11_111_111L
    private val hz120 = 8_333_333L

    @Test
    fun aCapIsSleptOutOnlyWhenThePresentCannotPaceIt() {
        val cap = FrameRateMode.Capped(30)
        assertEquals(FramePacer.Sleep, FramePacing.pacer(cap, presentPacing = false))
        assertEquals(FramePacer.Present, FramePacing.pacer(cap, presentPacing = true))
        assertEquals(23_333_333L, FramePacing.sleepNanos(cap, FramePacer.Sleep, frameWorkNanos = 10_000_000L))
        assertEquals(0L, FramePacing.sleepNanos(cap, FramePacer.Present, frameWorkNanos = 10_000_000L), "never paced twice")
    }

    @Test
    fun autoIsPacedAtPresentWhenItCanBeAndLeftToVsyncOtherwise() {
        assertEquals(FramePacer.Present, FramePacing.pacer(FrameRateMode.Auto, presentPacing = true))
        assertEquals(FramePacer.None, FramePacing.pacer(FrameRateMode.Auto, presentPacing = false))
        assertEquals(0L, FramePacing.sleepNanos(FrameRateMode.Auto, FramePacer.None, 0L))
    }

    @Test
    fun unlimitedIsNeverPaced() {
        assertEquals(FramePacer.None, FramePacing.pacer(FrameRateMode.Unlimited, presentPacing = true))
        assertEquals(FramePacer.None, FramePacing.pacer(FrameRateMode.Unlimited, presentPacing = false))
    }

    @Test
    fun aFrameOverItsBudgetSleepsNothing() {
        assertEquals(0L, FramePacing.sleepNanos(FrameRateMode.Capped(60), FramePacer.Sleep, frameWorkNanos = 40_000_000L))
    }

    @Test
    fun theSwapIntervalIsAWholeNumberOfRefreshes() {
        assertEquals(hz60, FramePacing.swapIntervalNanos(60, hz60))
        assertEquals(2 * hz60, FramePacing.swapIntervalNanos(30, hz60))
        assertEquals(2 * hz90, FramePacing.swapIntervalNanos(60, hz90), "60 on 90 Hz is 45 fps")
        assertEquals(2 * hz90, FramePacing.swapIntervalNanos(45, hz90))
        assertEquals(2 * hz120, FramePacing.swapIntervalNanos(60, hz120))
        assertEquals(hz60, FramePacing.swapIntervalNanos(240, hz60), "at least one refresh")
    }

    @Test
    fun theDisplayPeriodIsTheOneThatHitsTheTarget() {
        assertEquals(hz60, FramePacing.displayPeriodNanos(60, longArrayOf(hz90, hz60)))
        assertEquals(hz90, FramePacing.displayPeriodNanos(45, longArrayOf(hz90, hz60)))
        assertEquals(hz120, FramePacing.displayPeriodNanos(60, longArrayOf(hz60, hz120)), "faster on a tie")
        assertEquals(hz90, FramePacing.displayPeriodNanos(90, longArrayOf(hz60, hz90, hz120)))
        assertNull(FramePacing.displayPeriodNanos(60, LongArray(0)))
    }
}
