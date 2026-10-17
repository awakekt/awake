/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.releaseGamepadButtons
import com.awakekt.awake.core.input.setGamepadButtons
import platform.GameController.GCController
import platform.GameController.GCControllerButtonInput
import platform.GameController.GCExtendedGamepad

/**
 * Reads the controller the player last used, through the GameController framework, into [input]'s
 * controller buttons. GameController reports buttons as state rather than events, so a host calls
 * this once a frame; with no extended gamepad connected every button is released.
 */
fun pollGameController(input: Input) {
    val controller = GCController.current ?: GCController.controllers().firstOrNull() as? GCController
    val gamepad = controller?.extendedGamepad
    if (gamepad == null) {
        input.releaseGamepadButtons()
    } else {
        input.setGamepadButtons { gamepad.button(it)?.pressed == true }
    }
}

/** The input GameController reports [button] as, or null for one this controller lacks. */
@Suppress("CyclomaticComplexMethod") // One branch per GamepadButton.
private fun GCExtendedGamepad.button(button: GamepadButton): GCControllerButtonInput? = when (button) {
    GamepadButton.South -> buttonA
    GamepadButton.East -> buttonB
    GamepadButton.West -> buttonX
    GamepadButton.North -> buttonY
    GamepadButton.LeftShoulder -> leftShoulder
    GamepadButton.RightShoulder -> rightShoulder
    GamepadButton.LeftTrigger -> leftTrigger
    GamepadButton.RightTrigger -> rightTrigger
    GamepadButton.Select -> buttonOptions
    GamepadButton.Start -> buttonMenu
    GamepadButton.Home -> buttonHome
    GamepadButton.LeftStick -> leftThumbstickButton
    GamepadButton.RightStick -> rightThumbstickButton
    GamepadButton.DpadUp -> dpad.up
    GamepadButton.DpadDown -> dpad.down
    GamepadButton.DpadLeft -> dpad.left
    GamepadButton.DpadRight -> dpad.right
}
