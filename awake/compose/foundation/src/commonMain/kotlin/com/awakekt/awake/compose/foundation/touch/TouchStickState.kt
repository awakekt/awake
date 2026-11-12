/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.touch

import com.awakekt.awake.core.input.StickVector
import com.awakekt.awake.core.input.TouchStickMath
import kotlin.math.sqrt

/** Where a [TouchStick]'s pad sits. */
enum class TouchStickMode {
    /** Centred in the control, where it always is. */
    Fixed,

    /** Appears where the thumb lands inside the control, and stays there until it lifts. */
    Floating,

    /** Appears where the thumb lands, and is dragged along when the thumb goes past its reach. */
    Dynamic,
}

/** Which directions a [TouchStick] reports. */
enum class TouchStickDirections(internal val steps: Int) {
    /** Any angle. */
    Analog(0),

    /** The nearest of up, down, left and right. */
    Four(4),

    /** The nearest of the four and the four diagonals between them. */
    Eight(8),
}

/**
 * A [TouchStick]'s live state, owned by the caller: pass the same one to read [x] and [y] anywhere,
 * such as in a game system. It holds the pointer that owns the stick and reuses one vector, so a
 * held finger allocates nothing. Positions are in pixels from the control's top-left.
 */
class TouchStickState {
    private val out = StickVector()

    /** The deflection from -1 to 1 to the right; 0 at rest. */
    val x: Float get() = out.x

    /** The vertical deflection from -1 to 1; up is negative, down is positive, as on screen. */
    val y: Float get() = out.y

    /** Whether a finger holds the stick. */
    val isHeld: Boolean get() = pointer != NONE

    internal var pointer: Long = NONE
        private set
    internal var centerX = Float.NaN
        private set
    internal var centerY = Float.NaN
        private set
    internal var knobX = 0f
        private set
    internal var knobY = 0f
        private set

    // Settings, set every build by TouchStick. Pixels, not dp, so a drag does no unit conversion.
    internal var mode = TouchStickMode.Fixed
    internal var reach = 0f
    internal var deadZone = 0f
    internal var power = 1
    internal var directions = TouchStickDirections.Analog
    internal var snapBack = true
    internal var width = 0f
    internal var height = 0f
    internal var onChange: ((Float, Float) -> Unit)? = null

    /** Takes [id] at ([px], [py]) unless another finger holds the stick; true when it did. */
    internal fun press(id: Long, px: Float, py: Float): Boolean {
        if (pointer != NONE) return false
        pointer = id
        if (mode == TouchStickMode.Fixed) {
            centerX = width / 2f
            centerY = height / 2f
        } else {
            centerX = clampInto(px, reach, width)
            centerY = clampInto(py, reach, height)
        }
        move(px, py)
        return true
    }

    /** Moves the knob to ([px], [py]), within [reach] of the pad's centre. */
    internal fun move(px: Float, py: Float) {
        var dx = px - centerX
        var dy = py - centerY
        val length = sqrt(dx * dx + dy * dy)
        if (length > reach && length > 0f) {
            val over = (length - reach) / length
            if (mode == TouchStickMode.Dynamic) {
                centerX += dx * over
                centerY += dy * over
            }
            dx *= reach / length
            dy *= reach / length
        }
        knobX = dx
        knobY = dy
        TouchStickMath.deflect(dx, dy, reach, deadZone, power, directions.steps, out)
        onChange?.invoke(out.x, out.y)
    }

    /** Lets go, as a release or a cancel does: the knob centres unless [snapBack] is off. */
    internal fun release() {
        if (pointer == NONE) return
        pointer = NONE
        if (snapBack) {
            knobX = 0f
            knobY = 0f
            out.set(0f, 0f)
        }
        if (mode != TouchStickMode.Fixed) {
            centerX = Float.NaN
            centerY = Float.NaN
        }
        onChange?.invoke(out.x, out.y)
    }

    /** The pad's centre across: where it was pressed, or where it rests when [restX] of the width. */
    internal fun padX(restX: Float): Float = when {
        !centerX.isNaN() -> centerX
        mode == TouchStickMode.Fixed -> width / 2f
        else -> clampInto(restX * width, reach, width)
    }

    /** The vertical half of [padX]. */
    internal fun padY(restY: Float): Float = when {
        !centerY.isNaN() -> centerY
        mode == TouchStickMode.Fixed -> height / 2f
        else -> clampInto(restY * height, reach, height)
    }

    private fun clampInto(at: Float, reach: Float, length: Float): Float =
        if (length <= 2f * reach) length / 2f else at.coerceIn(reach, length - reach)

    internal companion object {
        const val NONE = Long.MIN_VALUE
    }
}
