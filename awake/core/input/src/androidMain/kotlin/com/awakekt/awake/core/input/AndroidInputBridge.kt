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

private fun MotionEvent.syncTouches(input: Input): Boolean =
    syncAndroidTouches(input, actionMasked, actionIndex, pointerCount, ::getPointerId, ::getX, ::getY)

/**
 * The touch half of [syncAwakePointerInput], kept off [MotionEvent] so it is tested on the host: an
 * event's [action] (masked), its [actionIndex], and each of its [count] pointers by index.
 */
internal inline fun syncAndroidTouches(
    input: Input,
    action: Int,
    actionIndex: Int,
    count: Int,
    id: (Int) -> Int,
    x: (Int) -> Float,
    y: (Int) -> Float,
): Boolean {
    // The finger that lifts with ACTION_POINTER_UP is still listed in this event; every finger
    // lifts with ACTION_UP and ACTION_CANCEL.
    val lifted = when (action) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_MOVE -> -1
        MotionEvent.ACTION_POINTER_UP -> actionIndex
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ALL_LIFTED
        else -> return false
    }
    for (index in 0 until count) {
        val down = lifted != ALL_LIFTED && index != lifted
        input.setTouch(id(index).toLong(), x(index), y(index), down)
    }
    return true
}

internal const val ALL_LIFTED = Int.MAX_VALUE
