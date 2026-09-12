/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.LayoutDirection
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.bounds
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Size2D
import kotlin.math.min
import com.awakekt.awake.core.math2d.Rectangle as BoundsRectangle

/** A reusable geometry definition for `background`, `border`, and `clip`. */
interface Shape {
    fun createOutline(
        size: Size2D,
        density: Float,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): ShapeOutline
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

    /** An asymmetric rounded rectangle, with one resolved radius per physical corner. */
    data class RoundedCorners(
        override val bounds: BoundsRectangle,
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float,
        val path: DrawPath,
    ) : ShapeOutline

    /** Any outline that needs the existing path rendering and clipping machinery. */
    data class Generic(
        val path: DrawPath,
        override val bounds: BoundsRectangle = path.bounds(),
    ) : ShapeOutline
}

/** Compose's default shape. */
data object RectangleShape : Shape {
    override fun createOutline(size: Size2D, density: Float, layoutDirection: LayoutDirection): ShapeOutline =
        ShapeOutline.Rectangle(size.bounds())
}

/** A corner size that resolves against the measured shape bounds. */
sealed interface CornerSize {
    fun toPx(size: Size2D, density: Float): Float

    data class Dp(val value: com.awakekt.awake.compose.ui.unit.Dp) : CornerSize {
        override fun toPx(size: Size2D, density: Float): Float = value.value * density
    }

    data class Px(val value: Float) : CornerSize {
        override fun toPx(size: Size2D, density: Float): Float = value
    }

    data class Percent(val value: Int) : CornerSize {
        init {
            require(value in 0..100) { "Corner percentage must be between 0 and 100, was $value" }
        }

        override fun toPx(size: Size2D, density: Float): Float =
            min(size.width, size.height) * value / 100f
    }
}

/**
 * Compose-shaped rounded corners. Independent corners are required by joined controls such as a
 * button group; a uniform shape keeps the existing rounded-quad fast path.
 */
class RoundedCornerShape(
    val topStart: CornerSize = CornerSize.Dp(0.dp),
    val topEnd: CornerSize = CornerSize.Dp(0.dp),
    val bottomEnd: CornerSize = CornerSize.Dp(0.dp),
    val bottomStart: CornerSize = CornerSize.Dp(0.dp),
) : Shape {
    override fun createOutline(size: Size2D, density: Float, layoutDirection: LayoutDirection): ShapeOutline =
        createOutline(size.bounds(), density, layoutDirection = layoutDirection)

    fun createOutline(
        bounds: BoundsRectangle,
        density: Float,
        radiusInset: Float = 0f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): ShapeOutline {
        val size = Size2D(bounds.width, bounds.height)
        fun radius(value: CornerSize): Float = (value.toPx(size, density) - radiusInset).coerceAtLeast(0f)
        val physicalTopLeft = if (layoutDirection == LayoutDirection.Ltr) topStart else topEnd
        val physicalTopRight = if (layoutDirection == LayoutDirection.Ltr) topEnd else topStart
        val physicalBottomRight = if (layoutDirection == LayoutDirection.Ltr) bottomEnd else bottomStart
        val physicalBottomLeft = if (layoutDirection == LayoutDirection.Ltr) bottomStart else bottomEnd

        // Match CornerBasedShape: adjacent radii are resolved against the same minimum dimension
        // so the corners cannot overlap along either axis.
        val minimum = min(bounds.width, bounds.height)
        val topLeft = radius(physicalTopLeft).coerceAtMost(minimum)
        val topRight = radius(physicalTopRight).coerceAtMost(minimum)
        val bottomRight = radius(physicalBottomRight).coerceAtMost(minimum - topRight)
        val bottomLeft = radius(physicalBottomLeft).coerceAtMost(minimum - topLeft)
        if (topLeft == 0f && topRight == 0f && bottomRight == 0f && bottomLeft == 0f) {
            return ShapeOutline.Rectangle(bounds)
        }
        if (topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft) {
            return ShapeOutline.Rounded(bounds, topLeft)
        }
        val path = DrawShape.RoundedCorners(
            topLeft = topLeft.dp,
            topRight = topRight.dp,
            bottomRight = bottomRight.dp,
            bottomLeft = bottomLeft.dp,
        ).toPath(bounds)
        return ShapeOutline.RoundedCorners(
            bounds = bounds,
            topLeft = topLeft,
            topRight = topRight,
            bottomRight = bottomRight,
            bottomLeft = bottomLeft,
            path = path,
        )
    }

    override fun equals(other: Any?): Boolean = other is RoundedCornerShape &&
        topStart == other.topStart && topEnd == other.topEnd &&
        bottomEnd == other.bottomEnd && bottomStart == other.bottomStart

    override fun hashCode(): Int = arrayOf(topStart, topEnd, bottomEnd, bottomStart).contentHashCode()

    override fun toString(): String =
        "RoundedCornerShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart)"
}

/** Compose-shaped Dp corner sizes. */
fun RoundedCornerShape(all: Dp): RoundedCornerShape = RoundedCornerShape(
    CornerSize.Dp(all),
    CornerSize.Dp(all),
    CornerSize.Dp(all),
    CornerSize.Dp(all),
)

fun RoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): RoundedCornerShape = RoundedCornerShape(
    CornerSize.Dp(topStart),
    CornerSize.Dp(topEnd),
    CornerSize.Dp(bottomEnd),
    CornerSize.Dp(bottomStart),
)

/** Compose-shaped pixel corner sizes. */
fun RoundedCornerShape(all: Float): RoundedCornerShape = RoundedCornerShape(
    CornerSize.Px(all),
    CornerSize.Px(all),
    CornerSize.Px(all),
    CornerSize.Px(all),
)

fun RoundedCornerShape(
    topStart: Float = 0f,
    topEnd: Float = 0f,
    bottomEnd: Float = 0f,
    bottomStart: Float = 0f,
): RoundedCornerShape = RoundedCornerShape(
    CornerSize.Px(topStart),
    CornerSize.Px(topEnd),
    CornerSize.Px(bottomEnd),
    CornerSize.Px(bottomStart),
)

/** Compose-shaped percentage corner sizes, relative to the smaller shape dimension. */
fun RoundedCornerShape(allPercent: Int): RoundedCornerShape = RoundedCornerShape(
    CornerSize.Percent(allPercent),
    CornerSize.Percent(allPercent),
    CornerSize.Percent(allPercent),
    CornerSize.Percent(allPercent),
)

fun RoundedCornerShape(
    topStartPercent: Int = 0,
    topEndPercent: Int = 0,
    bottomEndPercent: Int = 0,
    bottomStartPercent: Int = 0,
): RoundedCornerShape = RoundedCornerShape(
    CornerSize.Percent(topStartPercent),
    CornerSize.Percent(topEndPercent),
    CornerSize.Percent(bottomEndPercent),
    CornerSize.Percent(bottomStartPercent),
)

/** A circle inscribed in the node's bounds. */
data object CircleShape : Shape {
    override fun createOutline(size: Size2D, density: Float, layoutDirection: LayoutDirection): ShapeOutline {
        val bounds = size.bounds()
        return ShapeOutline.Generic(DrawShape.Circle.toPath(bounds), bounds)
    }
}

private fun Size2D.bounds(): BoundsRectangle = BoundsRectangle(0f, 0f, width, height)
