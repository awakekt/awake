/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.Input
import kotlin.test.Test
import kotlin.test.assertEquals

/** A GLFW gamepad poll reaches [Input] as controller buttons named by position. */
class GlfwGamepadTest {
    @Test
    fun eachGlfwGamepadButtonIsOneAwakeButton() {
        assertEquals(17, GlfwGamepadLayout.size, "GLFW's 15 buttons and two triggers")
        assertEquals(GamepadButton.entries.toSet(), GlfwGamepadLayout.toSet(), "each button once")
        assertEquals(GamepadButton.South, GlfwGamepadLayout[0], "GLFW_GAMEPAD_BUTTON_A is the bottom face button")
        assertEquals(GamepadButton.North, GlfwGamepadLayout[3], "GLFW_GAMEPAD_BUTTON_Y is the top one")
        assertEquals(GamepadButton.Home, GlfwGamepadLayout[8], "GLFW_GAMEPAD_BUTTON_GUIDE")
        assertEquals(GamepadButton.DpadLeft, GlfwGamepadLayout[14], "GLFW_GAMEPAD_BUTTON_DPAD_LEFT, the last button")
        assertEquals(GamepadButton.RightTrigger, GlfwGamepadLayout[16])
    }

    @Test
    fun aPollHoldsWhatIsDownAndReleasesTheRest() {
        val input = Input()

        applyGlfwGamepad((1 shl 0) or (1 shl 4) or (1 shl 16), input)
        assertEquals(
            setOf(GamepadButton.South, GamepadButton.LeftShoulder, GamepadButton.RightTrigger),
            input.snapshot().gamepadButtonsDown,
        )

        applyGlfwGamepad(1 shl 4, input)
        val snapshot = input.snapshot()
        assertEquals(setOf(GamepadButton.LeftShoulder), snapshot.gamepadButtonsDown)
        assertEquals(setOf(GamepadButton.South, GamepadButton.RightTrigger), snapshot.gamepadButtonsReleased)
    }

    @Test
    fun aControllerThatGoesAwayLetsGoOfEveryButton() {
        val input = Input()
        applyGlfwGamepad(1 shl 0, input)
        input.snapshot()

        applyGlfwGamepad(NO_GAMEPAD, input)

        assertEquals(emptySet(), input.snapshot().gamepadButtonsDown)
    }
}
