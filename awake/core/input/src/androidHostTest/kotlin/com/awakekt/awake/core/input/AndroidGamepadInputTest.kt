/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** An Android controller's key codes and axes reach [Input] as buttons named by position. */
internal class AndroidGamepadInputTest {
    @Test
    fun eachControllerKeyCodeIsOneButton() {
        val codes = listOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )

        assertEquals(GamepadButton.entries.toSet(), codes.mapNotNull(::androidGamepadButton).toSet())
        assertEquals(GamepadButton.South, androidGamepadButton(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(GamepadButton.East, androidGamepadButton(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(GamepadButton.Home, androidGamepadButton(KeyEvent.KEYCODE_BUTTON_MODE))
        assertNull(androidGamepadButton(KeyEvent.KEYCODE_W))
    }

    @Test
    fun theHatAndTriggersHoldPastHalfWay() {
        val input = Input()

        applyAndroidGamepadAxes(hatX = -1f, hatY = 1f, leftTrigger = 0.7f, rightTrigger = 0.3f, input = input)

        assertEquals(
            setOf(GamepadButton.DpadLeft, GamepadButton.DpadDown, GamepadButton.LeftTrigger),
            input.snapshot().gamepadButtonsDown,
        )
        applyAndroidGamepadAxes(hatX = 0f, hatY = -1f, leftTrigger = 0f, rightTrigger = 1f, input = input)
        assertEquals(setOf(GamepadButton.DpadUp, GamepadButton.RightTrigger), input.snapshot().gamepadButtonsDown)
    }

    @Test
    fun anAxisTheControllerLacksLeavesItsKeyEventButtonsAlone() {
        val input = Input()
        input.setGamepadButton(GamepadButton.DpadUp, true)
        input.setGamepadButton(GamepadButton.LeftTrigger, true)

        applyAndroidGamepadAxes(hatX = null, hatY = null, leftTrigger = null, rightTrigger = 0f, input = input)

        assertEquals(setOf(GamepadButton.DpadUp, GamepadButton.LeftTrigger), input.snapshot().gamepadButtonsDown)
    }
}
