/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.releaseGamepadButtons

/** What a host's controller poll returns while no controller is connected. */
internal const val NO_GAMEPAD = -1

/**
 * Sets [input]'s controller buttons from one poll: `layout[n]` is held when bit n of [bits] is set.
 * [NO_GAMEPAD] releases every button, so one held as a controller went away does not stay down.
 */
internal fun applyGamepadBits(bits: Int, input: Input, layout: List<GamepadButton>) {
    if (bits == NO_GAMEPAD) {
        input.releaseGamepadButtons()
    } else {
        layout.forEachIndexed { bit, button -> input.setGamepadButton(button, bits and (1 shl bit) != 0) }
    }
}

/**
 * The W3C Gamepad API's standard layout: each button at its index in the `buttons` of a gamepad
 * whose `mapping` is `"standard"`.
 */
internal val StandardGamepadLayout: List<GamepadButton> = listOf(
    GamepadButton.South,
    GamepadButton.East,
    GamepadButton.West,
    GamepadButton.North,
    GamepadButton.LeftShoulder,
    GamepadButton.RightShoulder,
    GamepadButton.LeftTrigger,
    GamepadButton.RightTrigger,
    GamepadButton.Select,
    GamepadButton.Start,
    GamepadButton.LeftStick,
    GamepadButton.RightStick,
    GamepadButton.DpadUp,
    GamepadButton.DpadDown,
    GamepadButton.DpadLeft,
    GamepadButton.DpadRight,
    GamepadButton.Home,
)
