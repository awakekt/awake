/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.math.abs

/** The kind of device a player last played with: what on-screen touch controls show or hide by. */
enum class InputDevice {
    /** A finger on a touch screen. */
    Touch,

    /** A keyboard, a mouse, or a trackpad driving the pointer. */
    KeyboardMouse,

    /** A game controller. */
    Controller,
}

/**
 * Follows which [InputDevice] the player last used, one [InputSnapshot] at a time, so touch controls
 * can show the moment a finger lands and hide the moment a controller or a keyboard takes over.
 *
 * A frame counts as used by the device behind it: a touch, a controller button pressed, or a key,
 * mouse button or wheel, or the mouse moving more than [mouseMoveSlop] pixels from where it last
 * rested. A frame with none of them changes nothing, so a player resting their thumbs keeps the
 * controls they had. Touch wins a frame that has more than one, since a touch screen's own pointer
 * is mirrored as a mouse.
 *
 * @param mouseMoveSlop How far, in pixels, the mouse must move to count; a desk knocked by a sleeve
 * should not hide a phone player's controls on a laptop with both.
 */
class InputDeviceTracker(private val mouseMoveSlop: Float = DEFAULT_MOUSE_MOVE_SLOP) {
    /** The device last used, or null until any has been. */
    var last: InputDevice? = null
        private set

    private var restX = Float.NaN
    private var restY = Float.NaN

    /** Reads one frame's [snapshot], and answers the device last used as of it. */
    fun observe(snapshot: InputSnapshot): InputDevice? {
        val used = when {
            snapshot.touches.isNotEmpty() || snapshot.pointerFromTouch && snapshot.pointerDown -> InputDevice.Touch
            snapshot.gamepadButtonsPressed.isNotEmpty() -> InputDevice.Controller
            usedKeyboardOrMouse(snapshot) -> InputDevice.KeyboardMouse
            else -> null
        }
        // A finger moves the pointer too; the mouse is measured from where the finger left it.
        if (snapshot.pointerFromTouch) rest(snapshot)
        if (used != null) last = used
        return last
    }

    private fun usedKeyboardOrMouse(snapshot: InputSnapshot): Boolean =
        snapshot.keysPressed.isNotEmpty() || snapshot.buttonsPressed.isNotEmpty() ||
            snapshot.scrollDeltaX != 0f || snapshot.scrollDeltaY != 0f || mouseMoved(snapshot)

    /** Whether a mouse, not a finger, moved the pointer past the slop since it last rested. */
    private fun mouseMoved(snapshot: InputSnapshot): Boolean {
        if (snapshot.pointerFromTouch) return false
        val moved = !restX.isNaN() &&
            (abs(snapshot.pointerX - restX) > mouseMoveSlop || abs(snapshot.pointerY - restY) > mouseMoveSlop)
        if (moved || restX.isNaN()) rest(snapshot)
        return moved
    }

    private fun rest(snapshot: InputSnapshot) {
        restX = snapshot.pointerX
        restY = snapshot.pointerY
    }

    /** Defaults. */
    companion object {
        /** How far, in pixels, the mouse moves before it counts, by default. */
        const val DEFAULT_MOUSE_MOVE_SLOP = 12f
    }
}
