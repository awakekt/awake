/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TouchInputTest {
    @Test
    fun everyFingerReachesTheSnapshotWithItsOwnEdges() {
        val input = Input()
        input.setTouch(0, 10f, 20f, down = true)
        input.setTouch(1, 200f, 20f, down = true)

        val both = input.updateSnapshot()
        assertEquals(listOf(TouchPoint(0, 10f, 20f, true, pressed = true), TouchPoint(1, 200f, 20f, true, pressed = true)), both.touches)

        input.setTouch(1, 180f, 25f, down = true)
        assertEquals(TouchPoint(1, 180f, 25f, true), input.updateSnapshot().touches[1], "a held finger is no longer pressed")
    }

    @Test
    fun theFirstFingerDrivesThePrimaryPointerAndASecondNeverTakesItOver() {
        val input = Input()
        input.setTouch(0, 10f, 20f, down = true)
        input.setTouch(1, 200f, 20f, down = true)
        input.setTouch(1, 220f, 40f, down = true)

        val held = input.updateSnapshot()
        assertTrue(held.pointerDown && held.pointerFromTouch)
        assertEquals(10f to 20f, held.pointerX to held.pointerY, "the second finger moved, not the first")

        input.setTouch(0, 12f, 22f, down = false)
        val lifted = input.updateSnapshot()
        assertTrue(lifted.pointerReleased && !lifted.pointerDown)
        assertEquals(listOf(0L, 1L), lifted.touches.map { it.id }, "the lifted finger shows once more")
        assertTrue(lifted.touches.first().released)

        input.setTouch(1, 230f, 40f, down = true)
        val after = input.updateSnapshot()
        assertEquals(listOf(1L), after.touches.map { it.id })
        assertFalse(after.pointerDown, "the remaining finger doesn't jump into the primary pointer")
    }

    @Test
    fun aTapInsideOneFrameKeepsBothEdges() {
        val input = Input()
        input.setTouch(3, 5f, 5f, down = true)
        input.setTouch(3, 5f, 5f, down = false)

        val tap = input.updateSnapshot().touches.single()
        assertTrue(tap.pressed && tap.released && !tap.down)
        assertTrue(input.updateSnapshot().touches.isEmpty())
    }

    @Test
    fun aMouseTakesThePrimaryPointerBack() {
        val input = Input()
        input.setTouch(0, 10f, 20f, down = true)
        input.setTouch(0, 10f, 20f, down = false)
        input.updateSnapshot()

        input.setPointer(down = false, x = 50f, y = 60f)
        assertFalse(input.updateSnapshot().pointerFromTouch)
    }
}
