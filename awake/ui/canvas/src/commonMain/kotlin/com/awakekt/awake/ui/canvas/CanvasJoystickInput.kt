/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * Feeds a joystick directly, for a host or deterministic test without a UI pointer. [x] and [y]
 * are normalized pad coordinates, right/down positive, clamped to its reach. The widget's dead zone
 * and sprint threshold apply exactly as they do to a pointer. `(0, 0)` centres it even with snap-back
 * disabled. This does not capture a pointer; the next pointer input can take over normally.
 *
 * @throws IllegalArgumentException if this is not a joystick, or either coordinate is non-finite.
 * @throws IllegalStateException if a real pointer currently holds the joystick.
 */
fun CanvasElement.setJoystickInput(x: Float, y: Float) {
    require(widget is CanvasJoystickWidget) { "Joystick input requires a CanvasJoystickWidget" }
    pointer.stick.setInput(x, y, this)
}
