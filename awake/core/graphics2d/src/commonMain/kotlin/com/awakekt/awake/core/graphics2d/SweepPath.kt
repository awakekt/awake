/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * [fraction] of a full turn about the centre of [bounds], as a radial sweep for a cooldown, a timer
 * or a radial gauge: from [startDegrees], clockwise from 12 o'clock, turning clockwise unless
 * [clockwise] is false.
 *
 * With a [thickness] of 0 it is a pie wedge whose radius reaches past the box's corners, so it covers
 * the whole box at a full turn: clip it to the box, or to any [DrawShape] mask such as a rounded
 * square or a circle, to give it that outline. With a [thickness] above 0 it is a ring's sector,
 * [thickness] wide inside the circle inscribed in [bounds].
 *
 * Null when [fraction] is 0 or less, or not a number: nothing is swept. A pie at a full turn is the
 * box itself, and a ring the whole ring, so neither closes an arc back onto its own start.
 */
fun sweepPath(
    bounds: Rectangle,
    fraction: Float,
    startDegrees: Float = 0f,
    clockwise: Boolean = true,
    thickness: Float = 0f,
): DrawPath? {
    // Not above 0 also catches NaN.
    if (!(fraction > 0f) || bounds.width <= 0f || bounds.height <= 0f) return null
    val amount = min(fraction, 1f)
    val centerX = bounds.x + bounds.width / 2f
    val centerY = bounds.y + bounds.height / 2f
    // arcTo's angles run clockwise from the positive X axis; 12 o'clock is 270 there.
    val start = startDegrees + TWELVE_O_CLOCK
    val sweep = (if (clockwise) 1f else -1f) * amount * FULL_TURN
    return if (thickness <= 0f) pie(bounds, centerX, centerY, start, sweep, amount) else ring(bounds, centerX, centerY, start, sweep, amount, thickness)
}

@Suppress("LongParameterList")
private fun pie(bounds: Rectangle, centerX: Float, centerY: Float, start: Float, sweep: Float, amount: Float): DrawPath {
    if (amount >= 1f) return DrawShape.Rectangle.toPath(bounds)
    val radius = hypot(bounds.width, bounds.height)
    val radians = start * DEGREES_TO_RADIANS
    return drawPath {
        moveTo(centerX, centerY)
        lineTo(centerX + radius * cos(radians), centerY + radius * sin(radians))
        arcTo(centerX - radius, centerY - radius, centerX + radius, centerY + radius, start, sweep)
        close()
    }
}

@Suppress("LongParameterList")
private fun ring(bounds: Rectangle, centerX: Float, centerY: Float, start: Float, sweep: Float, amount: Float, thickness: Float): DrawPath {
    val outer = min(bounds.width, bounds.height) / 2f
    val inner = (outer - thickness).coerceAtLeast(0f)
    if (amount >= 1f) {
        // Two whole circles, the inner one a hole: even-odd leaves what lies between them.
        val outerBox = Rectangle(centerX - outer, centerY - outer, outer * 2f, outer * 2f)
        return drawPath(FillRule.EvenOdd) {
            circle(outerBox)
            if (inner > 0f) circle(Rectangle(centerX - inner, centerY - inner, inner * 2f, inner * 2f))
        }
    }
    val from = start * DEGREES_TO_RADIANS
    val to = (start + sweep) * DEGREES_TO_RADIANS
    return drawPath {
        moveTo(centerX + outer * cos(from), centerY + outer * sin(from))
        arcTo(centerX - outer, centerY - outer, centerX + outer, centerY + outer, start, sweep)
        if (inner > 0f) {
            lineTo(centerX + inner * cos(to), centerY + inner * sin(to))
            arcTo(centerX - inner, centerY - inner, centerX + inner, centerY + inner, start + sweep, -sweep)
        } else {
            lineTo(centerX, centerY)
        }
        close()
    }
}

/** A whole circle in [box], as two half turns so its end never lands exactly back on its start. */
private fun PathBuilder.circle(box: Rectangle) {
    val radius = box.width / 2f
    moveTo(box.x + box.width, box.y + radius)
    arcTo(box.x, box.y, box.x + box.width, box.y + box.height, 0f, HALF_TURN)
    arcTo(box.x, box.y, box.x + box.width, box.y + box.height, HALF_TURN, HALF_TURN)
    close()
}

private const val TWELVE_O_CLOCK = 270f
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()
