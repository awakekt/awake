/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SweepPathTest {
    private val box = Rectangle(0f, 0f, 100f, 100f)

    @Test
    fun aQuarterClockwiseFromTwelveCoversTheTopRight() {
        val pie = sweepPath(box, 0.25f)!!

        assertTrue(pie.containsPoint(75f, 25f), "top right")
        assertFalse(pie.containsPoint(25f, 25f), "top left")
        assertFalse(pie.containsPoint(75f, 75f), "bottom right")
    }

    @Test
    fun counterClockwiseTurnsTheOtherWay() {
        val pie = sweepPath(box, 0.25f, clockwise = false)!!

        assertTrue(pie.containsPoint(25f, 25f))
        assertFalse(pie.containsPoint(75f, 25f))
    }

    @Test
    fun theStartAngleTurnsWhereItBegins() {
        // From 3 o'clock, a quarter clockwise is the bottom right.
        val pie = sweepPath(box, 0.25f, startDegrees = 90f)!!

        assertTrue(pie.containsPoint(75f, 75f))
        assertFalse(pie.containsPoint(75f, 25f))
    }

    @Test
    fun aPieReachesTheBoxsCornersSoAMaskGivesItsOutline() {
        val pie = sweepPath(box, 0.5f)!!

        assertTrue(pie.containsPoint(99f, 1f), "the top-right corner, outside the inscribed circle")
    }

    @Test
    fun aRingLeavesItsMiddleOut() {
        val ring = sweepPath(box, 0.5f, thickness = 10f)!!

        assertTrue(ring.containsPoint(95f, 50f), "on the ring at 3 o'clock")
        assertFalse(ring.containsPoint(80f, 50f), "inside it")
        assertFalse(ring.containsPoint(5f, 50f), "past half a turn")
        val whole = sweepPath(box, 1f, thickness = 10f)!!
        assertTrue(whole.containsPoint(5f, 50f) && whole.containsPoint(50f, 95f))
        assertFalse(whole.containsPoint(50f, 50f), "a whole ring is still hollow")
    }

    @Test
    fun nothingIsSweptAtZeroAndAWholePieIsTheBox() {
        assertNull(sweepPath(box, 0f))
        assertNull(sweepPath(box, Float.NaN))
        assertEquals(DrawShape.Rectangle.toPath(box), sweepPath(box, 1f))
        assertEquals(DrawShape.Rectangle.toPath(box), sweepPath(box, 3f), "above 1 is a whole turn")
    }
}
