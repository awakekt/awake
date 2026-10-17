/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.host.NativeLibrary
import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.Input

/** The first connected controller, through GLFW's gamepad API in `libawake-window`. */
internal object GlfwGamepad {
    init {
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            NativeLibrary.load(WINDOWS_GLFW_LIBRARY, GlfwGamepad::class.java)
        }
        NativeLibrary.load(WINDOW_LIBRARY, GlfwGamepad::class.java)
    }

    /**
     * The first controller GLFW has a gamepad mapping for, as bits in [GlfwGamepadLayout] order, or
     * [NO_GAMEPAD]. Call it on the thread that runs [GlfwWindow.glfwPollEvents].
     */
    external fun glfwPollGamepadButtons(): Int
}

/**
 * GLFW's gamepad layout: each button at its `GLFW_GAMEPAD_BUTTON_*` number, then the left and right
 * triggers pulled past half way.
 */
internal val GlfwGamepadLayout: List<GamepadButton> = listOf(
    GamepadButton.South,
    GamepadButton.East,
    GamepadButton.West,
    GamepadButton.North,
    GamepadButton.LeftShoulder,
    GamepadButton.RightShoulder,
    GamepadButton.Select,
    GamepadButton.Start,
    GamepadButton.Home,
    GamepadButton.LeftStick,
    GamepadButton.RightStick,
    GamepadButton.DpadUp,
    GamepadButton.DpadRight,
    GamepadButton.DpadDown,
    GamepadButton.DpadLeft,
    GamepadButton.LeftTrigger,
    GamepadButton.RightTrigger,
)

/**
 * Reads the first connected controller into [input]'s controller buttons. While [focused] is false,
 * or no controller is connected, every button is released, so one held as the window lost focus or
 * the controller went away does not stay down.
 */
fun pollGlfwGamepad(input: Input, focused: Boolean = true): Unit =
    applyGlfwGamepad(if (focused) GlfwGamepad.glfwPollGamepadButtons() else NO_GAMEPAD, input)

/** Testable core of [pollGlfwGamepad]: [bits] is what [GlfwGamepad.glfwPollGamepadButtons] returned. */
internal fun applyGlfwGamepad(bits: Int, input: Input): Unit = applyGamepadBits(bits, input, GlfwGamepadLayout)
