// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.unit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstraintsTest {

    @Test
    fun allFourFieldsRoundTripIndependently() {
        val c = Constraints.of(minWidth = 12, maxWidth = 340, minHeight = 7, maxHeight = 1080)
        assertEquals(12, c.minWidth)
        assertEquals(340, c.maxWidth)
        assertEquals(7, c.minHeight)
        assertEquals(1080, c.maxHeight)
    }

    @Test
    fun infinityRoundTripsAndReportsUnbounded() {
        val c = Constraints.of(0, 800, 0, Constraints.Infinity)
        assertEquals(Constraints.Infinity, c.maxHeight)
        assertTrue(c.hasBoundedWidth)
        assertFalse(c.hasBoundedHeight)
    }

    @Test
    fun theLargestRealDimensionIsNotMistakenForInfinity() {
        // The packing reserves 0xFFFF for Infinity, so the value just below it is the boundary
        // case that would break if the sentinel and a real size ever collided.
        val c = Constraints.of(0, Constraints.MaxDimension, 0, Constraints.MaxDimension)
        assertEquals(Constraints.MaxDimension, c.maxWidth)
        assertTrue(c.hasBoundedWidth)
    }

    @Test
    fun offsetLeavesAnUnboundedAxisUnbounded() {
        // Compose's Infinity is Int.MAX_VALUE, so `maxHeight - padding` there silently wraps to a
        // huge finite number and the axis stops being unbounded.
        val c = Constraints.of(0, 800, 0, Constraints.Infinity).offset(dx = -32, dy = -32)
        assertEquals(768, c.maxWidth)
        assertFalse(c.hasBoundedHeight)
        assertEquals(Constraints.Infinity, c.maxHeight)
    }

    @Test
    fun offsetFloorsAtZeroRatherThanGoingNegative() {
        val c = Constraints.of(4, 10, 4, 10).offset(dx = -100, dy = -100)
        assertEquals(0, c.minWidth)
        assertEquals(0, c.maxWidth)
        assertEquals(0, c.minHeight)
        assertEquals(0, c.maxHeight)
    }

    @Test
    fun constrainClampsIntoBounds() {
        val c = Constraints.of(10, 100, 10, 100)
        assertEquals(10, c.constrainWidth(0))
        assertEquals(100, c.constrainWidth(999))
        assertEquals(50, c.constrainHeight(50))
    }

    @Test
    fun fixedIsTightOnBothAxes() {
        val c = Constraints.fixed(640, 480)
        assertEquals(640, c.minWidth)
        assertEquals(640, c.maxWidth)
        assertEquals(480, c.minHeight)
        assertEquals(480, c.maxHeight)
    }

    @Test
    fun rejectsMaxBelowMin() {
        assertFailsWith<IllegalArgumentException> { Constraints.of(100, 50, 0, 0) }
    }

    @Test
    fun rejectsNegativeAndOversizedDimensions() {
        assertFailsWith<IllegalArgumentException> { Constraints.of(-1, 10, 0, 0) }
        assertFailsWith<IllegalArgumentException> {
            Constraints.of(0, Constraints.MaxDimension + 1, 0, 0)
        }
    }
}
