/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import android.view.MotionEvent

/**
 * Synchronizes an Android pointer [MotionEvent] into the Awake [input] accumulator.
 *
 * Fingers and styluses go in as touches, every one of them, so the UI sees multi-finger gestures;
 * the first finger down also drives the primary pointer (see [Input.setTouch]). A mouse, such as on
 * Samsung DeX, stays the primary pointer with its secondary button.
 *
 * @param input Target Awake [Input] accumulator receiving pointer states and coordinates.
 * @return True if the event was processed by the bridge, false otherwise.
 */
fun MotionEvent.syncAwakePointerInput(input: Input): Boolean =
    if (getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) syncMouse(input) else syncTouches(input)

private fun MotionEvent.syncMouse(input: Input): Boolean {
    val secondary = buttonState and MotionEvent.BUTTON_SECONDARY != 0
    return when (actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
            input.setSecondaryPointer(secondary)
            // A right-click must not also read as a primary press, or it would activate
            // whatever widget it opened the context menu over.
            input.setPointer(down = !secondary, x = x, y = y)
            true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            input.setSecondaryPointer(false)
            input.setPointer(down = false, x = x, y = y)
            true
        }
        else -> false
    }
}

private fun MotionEvent.syncTouches(input: Input): Boolean {
    // The finger that lifts with ACTION_POINTER_UP is still listed in this event; every finger
    // lifts with ACTION_UP and ACTION_CANCEL.
    val lifted = when (actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> -1
        MotionEvent.ACTION_POINTER_UP -> actionIndex
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ALL
        else -> return false
    }
    for (index in 0 until pointerCount) {
        val down = lifted != ALL && index != lifted
        input.setTouch(getPointerId(index).toLong(), getX(index), getY(index), down)
    }
    return true
}

private const val ALL = Int.MAX_VALUE
