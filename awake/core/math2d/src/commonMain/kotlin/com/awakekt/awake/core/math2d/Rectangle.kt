/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math2d

import kotlin.math.roundToInt

/** Immutable measured bounds shared by every public UI layer. */
data class Rectangle(val x: Float, val y: Float, val width: Float, val height: Float)

typealias Bounds = Rectangle

/** Clamps this rect to the region it shares with [other] -- zero-size if they do not overlap. */
fun Rectangle.intersect(other: Rectangle): Rectangle {
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    return Rectangle(left, top, (right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
}

/** True when [other] lies entirely within this rect (on-edge counts as contained). */
fun Rectangle.contains(other: Rectangle): Boolean =
    other.x >= x &&
        other.y >= y &&
        other.x + other.width <= x + width &&
        other.y + other.height <= y + height

/** True when the point ([px], [py]) lies within this rect (on-edge counts as contained). */
fun Rectangle.contains(px: Float, py: Float): Boolean =
    px >= x && px <= x + width && py >= y && py <= y + height

/** Snaps a coordinate to the nearest integer pixel to avoid subpixel rendering blur. */
fun pixelPerfectPixel(value: Float): Float = value.roundToInt().toFloat()
