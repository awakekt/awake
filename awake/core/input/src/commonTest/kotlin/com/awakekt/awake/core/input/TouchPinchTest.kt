/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [TouchPinch.update] reads the fingers [Input] already holds, as a bridge does that reports only
 * the fingers that changed in each event, such as UIKit's.
 */
internal class TouchPinchTest {
    private val input = Input()
    private val pinch = TouchPinch()

    /** One platform event: the fingers it changed, then the pinch read from every finger down. */
    private fun event(vararg changed: Triple<Long, Float, Boolean>, y: Float = 500f) {
        for ((id, x, down) in changed) input.setTouch(id, x, y, down)
        pinch.update(input)
    }

    @Test
    fun twoFingersMovingApartZoomInAboutTheirMidpoint() {
        event(Triple(1L, 400f, true))
        event(Triple(2L, 600f, true))
        input.snapshot()

        // UIKit sends each moved finger, here in one event.
        event(Triple(1L, 300f, true), Triple(2L, 700f, true))
        val gesture = assertNotNull(input.snapshot().pinch)

        assertEquals(2f, gesture.scale, 1e-4f)
        assertEquals(500f, gesture.focusX)
        assertEquals(500f, gesture.focusY)
    }

    @Test
    fun twoFingersMovingTogetherZoomOut() {
        event(Triple(1L, 300f, true), Triple(2L, 700f, true))
        input.snapshot()

        event(Triple(2L, 500f, true))
        val gesture = assertNotNull(input.snapshot().pinch)

        assertTrue(gesture.scale < 1f)
        assertEquals(0.5f, gesture.scale, 1e-4f)
    }

    @Test
    fun oneFingerGivesNoPinch() {
        event(Triple(1L, 100f, true))
        event(Triple(1L, 400f, true))
        event(Triple(1L, 400f, false))

        assertNull(input.snapshot().pinch)
    }

    @Test
    fun aPinchShowsInItsFrameOnly() {
        event(Triple(1L, 400f, true), Triple(2L, 600f, true))
        event(Triple(1L, 300f, true), Triple(2L, 700f, true))

        assertNotNull(input.snapshot().pinch)
        assertNull(input.snapshot().pinch)
    }

    @Test
    fun theFirstTwoFingersDownPinchAndAThirdDoesNot() {
        event(Triple(1L, 400f, true), Triple(2L, 600f, true))
        event(Triple(3L, 900f, true))
        input.snapshot()

        event(Triple(3L, 2000f, true))
        assertNull(input.snapshot().pinch, "the third finger is not one of the pair")
    }

    @Test
    fun aNewPairAfterAFingerLiftsDoesNotJump() {
        event(Triple(1L, 400f, true), Triple(2L, 600f, true))
        event(Triple(3L, 900f, true))
        event(Triple(2L, 600f, false))
        input.snapshot()

        // Fingers 1 and 3 now pinch, measured from where they were when finger 2 lifted.
        event(Triple(3L, 1400f, true))
        assertEquals(2f, assertNotNull(input.snapshot().pinch).scale, 1e-4f)
    }

    @Test
    fun liftingASecondFingerEndsThePinch() {
        event(Triple(1L, 400f, true), Triple(2L, 600f, true))
        event(Triple(2L, 600f, false))
        input.snapshot()

        // A new second finger starts a new pair: it sets the baseline, with no step from the old one.
        event(Triple(4L, 1000f, true))
        assertNull(input.snapshot().pinch)
    }
}
