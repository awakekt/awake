/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A browser's trackpad pinch reaches Input as a typed pinch, beside the Ctrl+wheel it already was. */
class BrowserPinchTest {

    @Test
    fun aCtrlWheelWithNoControlKeyIsAPinchAboutThePointer() {
        val input = Input()

        // Chromium sends a spread to 1.5x as deltaY = -100 * ln(1.5).
        BrowserPinch(input).wheel(-100.0 * ln(1.5), deltaMode = 0, ctrlKey = true, x = 40f, y = 30f)

        val pinch = input.updateSnapshot().pinch!!
        assertEquals(1.5f, pinch.scale, 1e-5f)
        assertEquals(40f to 30f, pinch.focusX to pinch.focusY)
    }

    @Test
    fun theWheelListenersCtrlScrollForAPinchIsItsZoomDelta() {
        // Both listeners on one pinch event, as the browser bridge runs them: the Ctrl+wheel path
        // (modifiers and scroll) and the typed pinch. An app reading either zooms the same amount.
        val input = Input()
        val modifiers = WheelModifiers(input) { }
        val deltaY = -100.0 * ln(1.2)

        modifiers.hold(wheelModifierKeys(ctrl = true, shift = false, alt = false, meta = false))
        input.scrollDeltaY += domWheelToScrollDelta(deltaY, deltaMode = 0)
        BrowserPinch(input).wheel(deltaY, deltaMode = 0, ctrlKey = true, x = 0f, y = 0f)

        val snapshot = input.updateSnapshot()
        assertTrue(snapshot.isDown(Key.Ctrl), "the Ctrl+wheel path is unchanged")
        assertEquals(snapshot.pinch!!.zoomDelta, snapshot.scrollDeltaY, 1e-5f)
    }

    @Test
    fun closingTheFingersZoomsOut() {
        assertEquals(exp(-0.2f), domPinchWheelScale(20.0, deltaMode = 0), 1e-6f)
    }

    @Test
    fun aPlainTwoFingerScrollIsNoPinch() {
        val input = Input()
        BrowserPinch(input).wheel(100.0, deltaMode = 0, ctrlKey = false, x = 0f, y = 0f)

        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun aCtrlWheelWithControlHeldIsNoPinch() {
        val input = Input()
        BrowserPinch(input).apply { controlHeld = true }.wheel(100.0, deltaMode = 0, ctrlKey = true, x = 0f, y = 0f)

        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun aStepIsClampedSoALineDeltaCannotJumpTheZoom() {
        assertEquals(0.5f, domPinchWheelScale(3.0, DOM_DELTA_LINE))
        assertEquals(2f, domPinchWheelScale(-1.0, DOM_DELTA_PAGE))
    }

    @Test
    fun safarisCumulativeGestureScaleArrivesAsSteps() {
        val input = Input()
        val pinch = BrowserPinch(input)

        pinch.gestureStart()
        pinch.gestureChange(1.2, 5f, 6f)
        assertEquals(1.2f, input.updateSnapshot().pinch!!.scale, 1e-6f)

        pinch.gestureChange(1.5, 5f, 6f)
        assertEquals(1.25f, input.updateSnapshot().pinch!!.scale, 1e-6f, "1.5 since the start is 1.25 since the last step")

        pinch.gestureEnd(1.5, 5f, 6f)
        assertNull(input.updateSnapshot().pinch, "an end at the last scale adds nothing")
    }

    @Test
    fun aCtrlWheelDuringASafariGestureIsNotASecondPinch() {
        val input = Input()
        val pinch = BrowserPinch(input)

        pinch.gestureStart()
        pinch.gestureChange(2.0, 0f, 0f)
        pinch.wheel(-50.0, deltaMode = 0, ctrlKey = true, x = 0f, y = 0f)

        assertEquals(2f, input.updateSnapshot().pinch!!.scale, 1e-6f)
    }

    @Test
    fun aGestureScaleThatIsNotPositiveIsIgnored() {
        val input = Input()
        val pinch = BrowserPinch(input)

        pinch.gestureStart()
        pinch.gestureChange(0.0, 0f, 0f)
        pinch.gestureChange(Double.NaN, 0f, 0f)
        pinch.gestureChange(0.5, 0f, 0f)

        assertEquals(0.5f, input.updateSnapshot().pinch!!.scale, 1e-6f, "the bad steps left the baseline at 1")
    }
}
