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
    /**
     * Resolves this shape against a node of [size] pixels.
     *
     * [density] converts dp-based corner sizes to pixels, and [layoutDirection] decides which physical
     * corner a start or end corner lands on.
     */
    fun createOutline(
        size: Size2D,
        density: Float,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): ShapeOutline
}

/** A shape resolved against one node's measured bounds. */
sealed interface ShapeOutline {
    /** The rectangle this outline was resolved against, in node-local pixels. */
    val bounds: BoundsRectangle

    /**
     * A rectangle that preserves the inexpensive quad rendering path.
     *
     * @property bounds The rectangle the outline fills.
     */
    data class Rectangle(override val bounds: BoundsRectangle) : ShapeOutline

    /**
     * A uniformly rounded rectangle that preserves the rounded-quad rendering path.
     *
     * @property bounds The rectangle the outline fills.
     * @property radius The corner radius in pixels, shared by all four corners.
     */
    data class Rounded(
        override val bounds: BoundsRectangle,
        val radius: Float,
    ) : ShapeOutline

    /**
     * An asymmetric rounded rectangle, with one resolved radius per physical corner.
     *
     * @property bounds The rectangle the outline fills.
     * @property topLeft The top-left corner radius in pixels.
     * @property topRight The top-right corner radius in pixels.
     * @property bottomRight The bottom-right corner radius in pixels.
     * @property bottomLeft The bottom-left corner radius in pixels.
     * @property path The same rounded rectangle as a path, for clipping and shadows.
     */
    data class RoundedCorners(
        override val bounds: BoundsRectangle,
        val topLeft: Float,
        val topRight: Float,
        val bottomRight: Float,
        val bottomLeft: Float,
        val path: DrawPath,
    ) : ShapeOutline

    /**
     * Any outline that needs the existing path rendering and clipping machinery.
     *
     * @property path The outline traced as a path, in node-local pixels.
     * @property bounds The rectangle enclosing the path, which defaults to the path's own bounds.
     */
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
    /** Resolves this corner size to pixels for a shape of [size] pixels at [density]. */
    fun toPx(size: Size2D, density: Float): Float

    /**
     * A corner size in dp, scaled by the density.
     *
     * @property value The radius in dp.
     */
    data class Dp(val value: com.awakekt.awake.compose.ui.unit.Dp) : CornerSize {
        override fun toPx(size: Size2D, density: Float): Float = value.value * density
    }

    /**
     * A corner size in raw pixels, unaffected by density.
     *
     * @property value The radius in pixels.
     */
    data class Px(val value: Float) : CornerSize {
        override fun toPx(size: Size2D, density: Float): Float = value
    }

    /**
     * A corner size as a share of the shape's shorter side.
     *
     * @property value The percentage, from 0 to 100.
     */
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
 *
 * @property topStart The top corner on the start side: top-left in left-to-right layouts, top-right otherwise.
 * @property topEnd The top corner on the end side: top-right in left-to-right layouts, top-left otherwise.
 * @property bottomEnd The bottom corner on the end side: bottom-right in left-to-right layouts.
 * @property bottomStart The bottom corner on the start side: bottom-left in left-to-right layouts.
 */
class RoundedCornerShape(
    val topStart: CornerSize = CornerSize.Dp(0.dp),
    val topEnd: CornerSize = CornerSize.Dp(0.dp),
    val bottomEnd: CornerSize = CornerSize.Dp(0.dp),
    val bottomStart: CornerSize = CornerSize.Dp(0.dp),
) : Shape {
    override fun createOutline(size: Size2D, density: Float, layoutDirection: LayoutDirection): ShapeOutline =
        createOutline(size.bounds(), density, layoutDirection = layoutDirection)

    /**
     * Resolves this shape against [bounds], subtracting [radiusInset] pixels from every corner radius.
     *
     * A radius floors at zero after the inset, so a negative [radiusInset] grows every corner. The
     * result is a rectangle when every corner resolves to zero, a uniform rounded outline when they
     * are all equal, and a per-corner outline otherwise.
     */
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

        // Oversized radii shrink together, as CSS border-radius does: when any side's two radii
        // add up to more than that side, every radius is scaled by the one factor that makes the
        // tightest side fit. A uniform `rounded-full` therefore lands every corner on half the
        // short side, keeping pills and circles symmetric, while a radius on one end only, like
        // `rounded-t-full`, can still reach the whole short side.
        val requestedTopLeft = radius(physicalTopLeft)
        val requestedTopRight = radius(physicalTopRight)
        val requestedBottomRight = radius(physicalBottomRight)
        val requestedBottomLeft = radius(physicalBottomLeft)
        val fit = minOf(
            1f,
            sideFit(bounds.width, requestedTopLeft + requestedTopRight),
            sideFit(bounds.height, requestedTopRight + requestedBottomRight),
            sideFit(bounds.width, requestedBottomRight + requestedBottomLeft),
            sideFit(bounds.height, requestedBottomLeft + requestedTopLeft),
        )
        val topLeft = requestedTopLeft * fit
        val topRight = requestedTopRight * fit
        val bottomRight = requestedBottomRight * fit
        val bottomLeft = requestedBottomLeft * fit
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

/** Creates a shape with a separate dp radius per corner, each defaulting to square. */
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

/** Creates a shape with a separate pixel radius per corner, each defaulting to square. */
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

/** Creates a shape with a separate percentage radius per corner, each defaulting to square. */
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

/** How far two corner radii on one side must shrink to fit its [length]; 1 or more when they fit. */
private fun sideFit(length: Float, radii: Float): Float = if (radii > length) length / radii else 1f
