/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics

import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.graphics2d.DrawPath
import io.github.awakelab.awake.core.graphics2d.DrawShape
import io.github.awakelab.awake.core.graphics2d.bounds
import io.github.awakelab.awake.core.graphics2d.toPath
import io.github.awakelab.awake.core.math2d.Size2D
import kotlin.math.min
import io.github.awakelab.awake.core.math2d.Rectangle as BoundsRectangle

/** A reusable geometry definition for `background`, `border`, and `clip`. */
interface Shape {
    fun createOutline(size: Size2D, density: Float): ShapeOutline
}

/** A shape resolved against one node's measured bounds. */
sealed interface ShapeOutline {
    val bounds: BoundsRectangle

    /** A rectangle that preserves the inexpensive quad rendering path. */
    data class Rectangle(override val bounds: BoundsRectangle) : ShapeOutline

    /** A uniformly rounded rectangle that preserves the rounded-quad rendering path. */
    data class Rounded(
        override val bounds: BoundsRectangle,
        val radius: Float,
    ) : ShapeOutline

    /** Any outline that needs the existing path rendering and clipping machinery. */
    data class Generic(
        val path: DrawPath,
        override val bounds: BoundsRectangle = path.bounds(),
    ) : ShapeOutline
}

/** Compose's default shape. */
data object RectangleShape : Shape {
    override fun createOutline(size: Size2D, density: Float): ShapeOutline =
        ShapeOutline.Rectangle(size.bounds())
}

/**
 * Compose-shaped rounded corners. Independent corners are required by joined controls such as a
 * button group; a uniform shape keeps the existing rounded-quad fast path.
 */
class RoundedCornerShape(
    val topStart: Dp = 0.dp,
    val topEnd: Dp = 0.dp,
    val bottomEnd: Dp = 0.dp,
    val bottomStart: Dp = 0.dp,
) : Shape {
    constructor(radius: Dp) : this(radius, radius, radius, radius)

    override fun createOutline(size: Size2D, density: Float): ShapeOutline =
        createOutline(size.bounds(), density)

    fun createOutline(bounds: BoundsRectangle, density: Float, radiusInset: Float = 0f): ShapeOutline {
        val limit = min(bounds.width, bounds.height) / 2f
        fun radius(value: Dp): Float = (value.value * density - radiusInset).coerceIn(0f, limit)
        val topLeft = radius(topStart)
        val topRight = radius(topEnd)
        val bottomRight = radius(bottomEnd)
        val bottomLeft = radius(bottomStart)
        if (topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft) {
            return ShapeOutline.Rounded(bounds, topLeft)
        }
        return ShapeOutline.Generic(
            DrawShape.RoundedCorners(
                topLeft = topLeft.dp,
                topRight = topRight.dp,
                bottomRight = bottomRight.dp,
                bottomLeft = bottomLeft.dp,
            ).toPath(bounds),
            bounds,
        )
    }

    override fun equals(other: Any?): Boolean = other is RoundedCornerShape &&
        topStart == other.topStart && topEnd == other.topEnd &&
        bottomEnd == other.bottomEnd && bottomStart == other.bottomStart

    override fun hashCode(): Int = arrayOf(topStart, topEnd, bottomEnd, bottomStart).contentHashCode()

    override fun toString(): String =
        "RoundedCornerShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart)"
}

/** A circle inscribed in the node's bounds. */
data object CircleShape : Shape {
    override fun createOutline(size: Size2D, density: Float): ShapeOutline {
        val bounds = size.bounds()
        return ShapeOutline.Generic(DrawShape.Circle.toPath(bounds), bounds)
    }
}

private fun Size2D.bounds(): BoundsRectangle = BoundsRectangle(0f, 0f, width, height)
