/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two fingers on a touch screen in a browser reach Input as a pinch, as the touch bridge feeds them:
 * every finger still down after each event, then [BrowserPinch.touch]. Safari also sends its own
 * gesture events for the same fingers, and those must not pinch a second time.
 */
class BrowserTouchPinchTest {
    private val input = Input()
    private val pinch = BrowserPinch(input)

    /** One touch event: the fingers still down, at these buffer x positions, as `TouchEvent.touches`. */
    private fun fingers(vararg down: Pair<Long, Float>) {
        for ((id, x) in down) input.setTouch(id, x, 300f, down = true)
        pinch.touch(down.size)
    }

    /** A `touchend` for [id]: it lifts, and [left] fingers stay down. */
    private fun lift(id: Long, x: Float, left: Int) {
        input.setTouch(id, x, 300f, down = false)
        pinch.touch(left)
    }

    @Test
    fun twoFingersMovingApartZoomInAboutTheirMidpoint() {
        fingers(0L to 400f)
        fingers(0L to 400f, 1L to 600f)
        input.updateSnapshot()

        fingers(0L to 300f, 1L to 700f)
        val gesture = assertNotNull(input.updateSnapshot().pinch)

        assertEquals(2f, gesture.scale, 1e-4f)
        assertEquals(500f to 300f, gesture.focusX to gesture.focusY)
    }

    @Test
    fun twoFingersMovingTogetherZoomOut() {
        fingers(0L to 300f, 1L to 700f)
        input.updateSnapshot()

        fingers(0L to 400f, 1L to 600f)
        val gesture = assertNotNull(input.updateSnapshot().pinch)

        assertTrue(gesture.scale < 1f)
        assertEquals(0.5f, gesture.scale, 1e-4f)
    }

    @Test
    fun oneFingerGivesNoPinch() {
        fingers(0L to 100f)
        fingers(0L to 400f)
        lift(0L, 400f, left = 0)

        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun aPinchShowsInItsFrameOnly() {
        fingers(0L to 400f, 1L to 600f)
        fingers(0L to 300f, 1L to 700f)

        assertNotNull(input.updateSnapshot().pinch)
        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun safarisGestureForTheSameFingersIsNotASecondPinch() {
        // Safari on a touch screen: touchstart for the second finger, then gesturestart.
        fingers(0L to 400f)
        fingers(0L to 400f, 1L to 600f)
        pinch.gestureStart()
        input.updateSnapshot()

        // The fingers spread 2x: Safari sends both the touchmove and a gesturechange of scale 2.
        fingers(0L to 300f, 1L to 700f)
        pinch.gestureChange(2.0, 0f, 0f)

        assertEquals(2f, assertNotNull(input.updateSnapshot().pinch).scale, 1e-4f, "one pinch, not 4x")
    }

    @Test
    fun safarisGestureEndAfterTheFingersLiftDoesNotJump() {
        fingers(0L to 400f, 1L to 600f)
        pinch.gestureStart()
        fingers(0L to 300f, 1L to 700f)
        pinch.gestureChange(2.0, 0f, 0f)
        input.updateSnapshot()

        // Safari ends the gesture once a finger has lifted, at the gesture's final scale.
        lift(1L, 700f, left = 1)
        pinch.gestureEnd(2.0, 0f, 0f)

        assertNull(input.updateSnapshot().pinch)
    }

    @Test
    fun aTrackpadGestureWithNoFingersStillPinches() {
        pinch.gestureStart()
        pinch.gestureChange(1.5, 10f, 20f)

        assertEquals(1.5f, assertNotNull(input.updateSnapshot().pinch).scale, 1e-6f)
    }
}
