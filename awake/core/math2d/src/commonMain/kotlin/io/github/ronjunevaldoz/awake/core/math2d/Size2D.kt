// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.math2d

/**
 * A 2D extent in pixels -- a width and a height with no position.
 *
 * Separate from [Rectangle] because a measured size and a placed rectangle are different facts, and
 * conflating them is how a layout ends up passing an origin where an extent was meant. A measure
 * pass produces a [Size2D]; only placement turns it into a [Rectangle].
 *
 * Float, not Int: this is post-density pixels, and rounding belongs at the rasterizer, not here.
 */
data class Size2D(val width: Float, val height: Float) {
    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    /** Both extents scaled by [factor] -- density conversion, or a uniform inset ratio. */
    operator fun times(factor: Float): Size2D = Size2D(width * factor, height * factor)

    companion object {
        val Zero = Size2D(0f, 0f)
    }
}

/** This rectangle's extent, dropping its origin. */
val Rectangle.size: Size2D get() = Size2D(width, height)

/** [size] placed with its top-left corner at ([x], [y]). */
fun Size2D.at(x: Float, y: Float): Rectangle = Rectangle(x, y, width, height)
