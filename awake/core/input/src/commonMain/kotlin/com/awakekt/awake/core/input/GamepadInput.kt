/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

/**
 * Holds or releases every controller button at once, as a host that polls a controller does each
 * frame: [isDown] answers for each [GamepadButton].
 */
inline fun Input.setGamepadButtons(isDown: (GamepadButton) -> Boolean) {
    for (button in GamepadButton.entries) setGamepadButton(button, isDown(button))
}

/** Releases every controller button, as a host does when its controller goes away or its window loses focus. */
fun Input.releaseGamepadButtons() {
    for (button in GamepadButton.entries) setGamepadButton(button, false)
}
