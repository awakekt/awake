/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [InputDeviceTracker]: which device the player last used, frame by frame. */
class InputDeviceTrackerTest {
    private val tracker = InputDeviceTracker()

    private fun frame(
        x: Float = 100f,
        y: Float = 100f,
        keys: Set<Key> = emptySet(),
        touches: List<TouchPoint> = emptyList(),
        fromTouch: Boolean = false,
        down: Boolean = false,
        gamepad: Set<GamepadButton> = emptySet(),
        scroll: Float = 0f,
    ) = tracker.observe(
        InputSnapshot(
            pointerX = x, pointerY = y, pointerDown = down, scrollDeltaX = 0f, scrollDeltaY = scroll,
            keysDown = keys, keysPressed = keys, keysReleased = emptySet(), typedText = "", editActions = emptyList(),
            touches = touches, pointerFromTouch = fromTouch, gamepadButtonsDown = gamepad, gamepadButtonsPressed = gamepad,
        ),
    )

    @Test
    fun nothingUsedIsNoDeviceAndAQuietFrameKeepsTheLast() {
        assertNull(frame())
        assertEquals(InputDevice.Touch, frame(fromTouch = true, down = true))
        assertEquals(InputDevice.Touch, frame(), "resting thumbs keep touch")
    }

    @Test
    fun eachDeviceTakesOverWhenItIsUsed() {
        assertEquals(InputDevice.Touch, frame(touches = listOf(TouchPoint(1, 10f, 10f, down = true))))
        assertEquals(InputDevice.Controller, frame(gamepad = setOf(GamepadButton.entries.first())))
        assertEquals(InputDevice.KeyboardMouse, frame(keys = setOf(Key.W)))
        assertEquals(InputDevice.Touch, frame(fromTouch = true, down = true))
        assertEquals(InputDevice.KeyboardMouse, frame(scroll = 1f))
    }

    @Test
    fun theMouseCountsOnlyOnceItMovesPastTheSlop() {
        frame(fromTouch = true, down = true, x = 100f, y = 100f)

        assertEquals(InputDevice.Touch, frame(x = 105f, y = 100f), "a nudge of 5 px is not the mouse")
        assertEquals(InputDevice.KeyboardMouse, frame(x = 130f, y = 100f), "30 px is")
    }
}
