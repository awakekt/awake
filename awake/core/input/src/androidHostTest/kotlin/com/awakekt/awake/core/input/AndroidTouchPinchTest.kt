/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import android.view.MotionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two fingers on an Android touch screen reach [Input] as a pinch; one finger never does. */
internal class AndroidTouchPinchTest {
    private val input = Input()

    /** Sends one event whose pointers are (id, x, y) in index order, as a [MotionEvent] lists them. */
    private fun event(action: Int, vararg pointers: Triple<Int, Float, Float>, actionIndex: Int = 0) {
        syncAndroidTouches(input, action, actionIndex, pointers.size, { pointers[it].first }, { pointers[it].second }, { pointers[it].third })
    }

    private fun twoFingersDown(left: Float, right: Float) {
        event(MotionEvent.ACTION_DOWN, Triple(0, left, 500f))
        event(MotionEvent.ACTION_POINTER_DOWN, Triple(0, left, 500f), Triple(1, right, 500f), actionIndex = 1)
    }

    @Test
    fun twoFingersMovingApartZoomInAboutTheirMidpoint() {
        twoFingersDown(400f, 600f)
        input.snapshot()

        event(MotionEvent.ACTION_MOVE, Triple(0, 300f, 500f), Triple(1, 700f, 500f))
        val pinch = assertNotNull(input.snapshot().pinch)

        assertEquals(2f, pinch.scale, 1e-4f)
        assertEquals(500f, pinch.focusX)
        assertEquals(500f, pinch.focusY)
    }

    @Test
    fun twoFingersMovingTogetherZoomOut() {
        twoFingersDown(300f, 700f)
        input.snapshot()

        event(MotionEvent.ACTION_MOVE, Triple(0, 350f, 500f), Triple(1, 650f, 500f))
        event(MotionEvent.ACTION_MOVE, Triple(0, 400f, 500f), Triple(1, 600f, 500f))
        val pinch = assertNotNull(input.snapshot().pinch)

        assertTrue(pinch.scale < 1f)
        assertEquals(0.5f, pinch.scale, 1e-4f)
    }

    @Test
    fun oneFingerGivesNoPinch() {
        event(MotionEvent.ACTION_DOWN, Triple(0, 100f, 100f))
        event(MotionEvent.ACTION_MOVE, Triple(0, 300f, 400f))
        event(MotionEvent.ACTION_UP, Triple(0, 300f, 400f))

        assertNull(input.snapshot().pinch)
    }

    @Test
    fun aPinchShowsInItsFrameOnly() {
        twoFingersDown(400f, 600f)
        event(MotionEvent.ACTION_MOVE, Triple(0, 300f, 500f), Triple(1, 700f, 500f))

        assertNotNull(input.snapshot().pinch)
        assertNull(input.snapshot().pinch)
    }

    @Test
    fun aSecondFingerPutDownSetsTheBaselineWithoutAStep() {
        twoFingersDown(100f, 900f)

        assertNull(input.snapshot().pinch)
    }

    @Test
    fun aNewPairAfterAFingerLiftsDoesNotJump() {
        event(MotionEvent.ACTION_DOWN, Triple(0, 400f, 500f))
        event(MotionEvent.ACTION_POINTER_DOWN, Triple(0, 400f, 500f), Triple(1, 600f, 500f), actionIndex = 1)
        event(MotionEvent.ACTION_POINTER_DOWN, Triple(0, 400f, 500f), Triple(1, 600f, 500f), Triple(2, 900f, 500f), actionIndex = 2)
        event(MotionEvent.ACTION_POINTER_UP, Triple(0, 400f, 500f), Triple(1, 600f, 500f), Triple(2, 900f, 500f), actionIndex = 1)
        input.snapshot()

        // Fingers 0 and 2 now pinch, measured from where they were when finger 1 lifted.
        event(MotionEvent.ACTION_MOVE, Triple(0, 400f, 500f), Triple(2, 1400f, 500f))
        assertEquals(2f, assertNotNull(input.snapshot().pinch).scale, 1e-4f)
    }

    @Test
    fun singleFingerStillDrivesThePrimaryPointer() {
        event(MotionEvent.ACTION_DOWN, Triple(0, 100f, 200f))
        event(MotionEvent.ACTION_MOVE, Triple(0, 150f, 250f))
        val snapshot = input.snapshot()

        assertTrue(snapshot.pointerDown)
        assertEquals(150f, snapshot.pointerX)
        assertEquals(250f, snapshot.pointerY)
        assertTrue(snapshot.pointerFromTouch)
    }
}
