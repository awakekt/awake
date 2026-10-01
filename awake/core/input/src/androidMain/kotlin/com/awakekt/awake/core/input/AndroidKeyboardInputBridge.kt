/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import android.view.KeyEvent

/** Default mapping between Android key codes and Awake [Key] equivalents for gameplay. */
val DefaultAndroidGameplayKeys: Map<Int, Key> = linkedMapOf(
    KeyEvent.KEYCODE_W to Key.W,
    KeyEvent.KEYCODE_A to Key.A,
    KeyEvent.KEYCODE_S to Key.S,
    KeyEvent.KEYCODE_D to Key.D,
    KeyEvent.KEYCODE_DPAD_UP to Key.ArrowUp,
    KeyEvent.KEYCODE_DPAD_DOWN to Key.ArrowDown,
    KeyEvent.KEYCODE_DPAD_LEFT to Key.ArrowLeft,
    KeyEvent.KEYCODE_DPAD_RIGHT to Key.ArrowRight,
    KeyEvent.KEYCODE_SPACE to Key.Space,
    KeyEvent.KEYCODE_ESCAPE to Key.Escape,
)

/**
 * Synchronizes an Android [KeyEvent] into the Awake [input] accumulator using the provided [keys] mapping.
 *
 * @param down True if this is a key-down event, false for key-up.
 * @param input Target Awake [Input] accumulator receiving key states.
 * @param keys Key code lookup table mapping Android key codes to Awake [Key] values.
 * @return True if the key was recognized and mapped, false otherwise.
 */
fun KeyEvent.syncAwakeKeyInput(
    down: Boolean,
    input: Input,
    keys: Map<Int, Key> = DefaultAndroidGameplayKeys,
): Boolean {
    val key = keys[keyCode] ?: return false
    input.setKeyDown(key, down)
    return true
}
