/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PinchGestureTest {
    @Test
    fun aFrameWithoutAPinchHasNone() {
        val input = Input()
        input.scrollDeltaY = 1f

        assertNull(input.updateSnapshot().pinch, "a scroll is not a pinch")
    }

    @Test
    fun stepsBeforeASnapshotMultiplyAboutTheLatestFocus() {
        val input = Input()
        input.addPinch(1.25f, 10f, 20f)
        input.addPinch(1.2f, 12f, 24f)

        val pinch = input.updateSnapshot().pinch!!
        assertEquals(1.5f, pinch.scale, 1e-6f)
        assertEquals(12f to 24f, pinch.focusX to pinch.focusY)
    }

    @Test
    fun aPinchShowsInOneSnapshotOnly() {
        val input = Input()
        input.addPinch(0.8f, 0f, 0f)

        assertEquals(0.8f, input.updateSnapshot().pinch?.scale)
        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun aScaleThatIsNotFiniteAndPositiveIsDropped() {
        val input = Input()
        input.addPinch(0f, 0f, 0f)
        input.addPinch(-2f, 0f, 0f)
        input.addPinch(Float.NaN, 0f, 0f)
        input.addPinch(Float.POSITIVE_INFINITY, 0f, 0f)

        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun stepsThatCancelOutLeaveNoPinch() {
        val input = Input()
        input.addPinch(2f, 0f, 0f)
        input.addPinch(0.5f, 0f, 0f)

        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun zoomDeltaIsTheLogOfTheScaleSoStepsAdd() {
        assertEquals(0f, PinchGesture(1f, 0f, 0f).zoomDelta)
        assertEquals(ln(2f), PinchGesture(2f, 0f, 0f).zoomDelta, 1e-6f)
        assertEquals(-ln(2f), PinchGesture(0.5f, 0f, 0f).zoomDelta, 1e-6f)
    }
}
