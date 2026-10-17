/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.max

/** How far a trigger or d-pad axis must go, of its full travel, for its button to count as held. */
private const val AXIS_PRESSED = 0.5f

/** The [GamepadButton] each controller key code is. */
private val ControllerKeyCodes: Map<Int, GamepadButton> = mapOf(
    KeyEvent.KEYCODE_BUTTON_A to GamepadButton.South,
    KeyEvent.KEYCODE_BUTTON_B to GamepadButton.East,
    KeyEvent.KEYCODE_BUTTON_X to GamepadButton.West,
    KeyEvent.KEYCODE_BUTTON_Y to GamepadButton.North,
    KeyEvent.KEYCODE_BUTTON_L1 to GamepadButton.LeftShoulder,
    KeyEvent.KEYCODE_BUTTON_R1 to GamepadButton.RightShoulder,
    KeyEvent.KEYCODE_BUTTON_L2 to GamepadButton.LeftTrigger,
    KeyEvent.KEYCODE_BUTTON_R2 to GamepadButton.RightTrigger,
    KeyEvent.KEYCODE_BUTTON_SELECT to GamepadButton.Select,
    KeyEvent.KEYCODE_BUTTON_START to GamepadButton.Start,
    KeyEvent.KEYCODE_BUTTON_MODE to GamepadButton.Home,
    KeyEvent.KEYCODE_BUTTON_THUMBL to GamepadButton.LeftStick,
    KeyEvent.KEYCODE_BUTTON_THUMBR to GamepadButton.RightStick,
    KeyEvent.KEYCODE_DPAD_UP to GamepadButton.DpadUp,
    KeyEvent.KEYCODE_DPAD_DOWN to GamepadButton.DpadDown,
    KeyEvent.KEYCODE_DPAD_LEFT to GamepadButton.DpadLeft,
    KeyEvent.KEYCODE_DPAD_RIGHT to GamepadButton.DpadRight,
)

/** The [GamepadButton] a controller's Android key code is, or null for a key code no controller button has. */
internal fun androidGamepadButton(keyCode: Int): GamepadButton? = ControllerKeyCodes[keyCode]

/**
 * Passes a controller's button [KeyEvent] to [input] as a [GamepadButton].
 *
 * Only an event from a gamepad or joystick counts, so a TV remote's d-pad stays the arrow keys. A
 * controller's d-pad sets its d-pad buttons here, and a host still passes it to
 * [syncAwakeKeyInput] too, which makes it the arrow keys as before.
 *
 * @param down True for a key-down event, false for key-up.
 * @param input Receives the button.
 * @return True when the event was a controller button.
 */
fun KeyEvent.syncAwakeGamepadInput(down: Boolean, input: Input): Boolean {
    val button = androidGamepadButton(keyCode)?.takeIf { isFromController(source) } ?: return false
    input.setGamepadButton(button, down)
    return true
}

/**
 * Passes a controller's analog triggers and hat d-pad, from a joystick [MotionEvent], to [input] as
 * [GamepadButton.LeftTrigger], [GamepadButton.RightTrigger] and the d-pad buttons. A trigger counts
 * as held past half its travel. An axis the controller does not have is left alone, so a controller
 * that sends its d-pad or triggers as key events keeps them.
 *
 * @param input Receives the buttons.
 * @return True when the event was a controller's movement.
 */
fun MotionEvent.syncAwakeGamepadInput(input: Input): Boolean {
    if (!isFromSource(InputDevice.SOURCE_JOYSTICK) || actionMasked != MotionEvent.ACTION_MOVE) return false
    applyAndroidGamepadAxes(
        hatX = axisOrNull(MotionEvent.AXIS_HAT_X),
        hatY = axisOrNull(MotionEvent.AXIS_HAT_Y),
        leftTrigger = maxOfPresent(axisOrNull(MotionEvent.AXIS_LTRIGGER), axisOrNull(MotionEvent.AXIS_BRAKE)),
        rightTrigger = maxOfPresent(axisOrNull(MotionEvent.AXIS_RTRIGGER), axisOrNull(MotionEvent.AXIS_GAS)),
        input = input,
    )
    return true
}

/**
 * Testable core of the joystick [MotionEvent] bridge. Each value is null when the controller lacks
 * that axis. The hat is -1 to 1, up and left negative, and a trigger 0 to 1.
 */
internal fun applyAndroidGamepadAxes(hatX: Float?, hatY: Float?, leftTrigger: Float?, rightTrigger: Float?, input: Input) {
    if (hatX != null) {
        input.setGamepadButton(GamepadButton.DpadLeft, hatX <= -AXIS_PRESSED)
        input.setGamepadButton(GamepadButton.DpadRight, hatX >= AXIS_PRESSED)
    }
    if (hatY != null) {
        input.setGamepadButton(GamepadButton.DpadUp, hatY <= -AXIS_PRESSED)
        input.setGamepadButton(GamepadButton.DpadDown, hatY >= AXIS_PRESSED)
    }
    if (leftTrigger != null) input.setGamepadButton(GamepadButton.LeftTrigger, leftTrigger >= AXIS_PRESSED)
    if (rightTrigger != null) input.setGamepadButton(GamepadButton.RightTrigger, rightTrigger >= AXIS_PRESSED)
}

private fun isFromController(source: Int): Boolean =
    source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

private fun MotionEvent.axisOrNull(axis: Int): Float? =
    if (device?.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK) != null) getAxisValue(axis) else null

private fun maxOfPresent(a: Float?, b: Float?): Float? = if (a == null) {
    b
} else if (b == null) {
    a
} else {
    max(a, b)
}
