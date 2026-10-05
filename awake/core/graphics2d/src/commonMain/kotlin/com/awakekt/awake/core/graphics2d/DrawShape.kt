/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.min

/**
 * Specification for geometric shapes used in drawing operations, clipping, and hit testing.
 */
sealed interface DrawShape {
    /**
     * A sharp-cornered rectangular shape.
     */
    data object Rectangle : DrawShape

    /**
     * A rectangle whose four corners share a uniform corner radius.
     *
     * @property radius The uniform corner radius in [Dp].
     */
    data class RoundedRectangle(val radius: Dp) : DrawShape

    /**
     * A circular shape inscribed within the bounding box.
     */
    data object Circle : DrawShape

    /**
     * A stadium or capsule shape with fully rounded end caps.
     */
    data object Pill : DrawShape

    /**
     * A rectangle with uniform chamfered or beveled cut corners.
     *
     * @property size The cut corner inset distance in [Dp].
     */
    data class CutCorner(val size: Dp) : DrawShape

    /**
     * A rectangle whose corners round independently -- Compose's `RoundedCornerShape(topStart,
     * topEnd, bottomEnd, bottomStart)`.
     *
     * @property topLeft The corner radius for the top-left corner in [Dp].
     * @property topRight The corner radius for the top-right corner in [Dp].
     * @property bottomRight The corner radius for the bottom-right corner in [Dp].
     * @property bottomLeft The corner radius for the bottom-left corner in [Dp].
     */
    data class RoundedCorners(
        val topLeft: Dp,
        val topRight: Dp,
        val bottomRight: Dp,
        val bottomLeft: Dp,
    ) : DrawShape

    /**
     * Factory methods and standard singleton shapes for [DrawShape].
     */
    companion object {
        /**
         * Singleton accessor for [DrawShape.Rectangle].
         */
        val Rectangle: DrawShape.Rectangle get() = DrawShape.Rectangle

        /**
         * Factory function creating a [DrawShape.RoundedRectangle] with the specified uniform [radius].
         *
         * @param radius The uniform corner radius in [Dp].
         * @return A [DrawShape.RoundedRectangle] with the given radius.
         */
        fun RoundedRectangle(radius: Dp): DrawShape.RoundedRectangle = DrawShape.RoundedRectangle(radius)

        /**
         * Singleton accessor for [DrawShape.Circle].
         */
        val Circle: DrawShape.Circle get() = DrawShape.Circle

        /**
         * Singleton accessor for [DrawShape.Pill].
         */
        val Pill: DrawShape.Pill get() = DrawShape.Pill

        /**
         * Factory function creating a [DrawShape.CutCorner] with the specified chamfer [size].
         *
         * @param size The cut corner size in [Dp].
         * @return A [DrawShape.CutCorner] with the given chamfer size.
         */
        fun CutCorner(size: Dp): DrawShape.CutCorner = DrawShape.CutCorner(size)

        /**
         * Factory function creating a [DrawShape.RoundedCorners] shape with independent corner radii.
         *
         * @param topLeft The radius of the top-left corner in [Dp].
         * @param topRight The radius of the top-right corner in [Dp].
         * @param bottomRight The radius of the bottom-right corner in [Dp].
         * @param bottomLeft The radius of the bottom-left corner in [Dp].
         * @return A [DrawShape.RoundedCorners] with the specified corner radii.
         */
        fun RoundedCorners(
            topLeft: Dp,
            topRight: Dp,
            bottomRight: Dp,
            bottomLeft: Dp,
        ): DrawShape.RoundedCorners = DrawShape.RoundedCorners(topLeft, topRight, bottomRight, bottomLeft)
    }
}

typealias UiShapeSpec = DrawShape

/**
 * Converts this geometric [DrawShape] into an executable [DrawPath] evaluated within the given [bounds].
 *
 * @param bounds The bounding rectangle enclosing the generated path.
 * @param fillRule The winding fill rule assigned to the path.
 * @param density Screen density factor scaling [Dp] dimensions to physical pixels.
 * @return A [DrawPath] describing the shape geometry.
 */
fun DrawShape.toPath(
    bounds: Rectangle,
    fillRule: FillRule = FillRule.NonZero,
    density: Float = 1f,
): DrawPath = when (this) {
    DrawShape.Rectangle -> rectanglePath(bounds, fillRule)
    is DrawShape.RoundedRectangle -> roundedRectanglePath(bounds, radius, density, fillRule)
    DrawShape.Circle -> circlePath(bounds, fillRule)
    DrawShape.Pill -> pillPath(bounds, fillRule)
    is DrawShape.CutCorner -> cutCornerPath(bounds, size, density, fillRule)
    is DrawShape.RoundedCorners -> roundedCornersPath(bounds, this, density, fillRule)
}

/**
 * Max inset that's safe to shrink a shape's own [bounds] by such that ANY primitive whose own
 * (axis-aligned) bounds fit entirely inside the shrunk rect provably cannot touch this shape's
 * rounded/cut corner region -- backs the "skip exact convex-path clipping" fast path in each
 * backend's `RendererDrawUi.kt`.
 *
 * @param bounds The bounding rectangle of the shape.
 * @param density Screen density factor scaling [Dp] dimensions to physical pixels.
 * @return The margin in pixels safe from corner clipping artifacts.
 */
fun DrawShape.safeInteriorMargin(bounds: Rectangle, density: Float = 1f): Float = when (this) {
    DrawShape.Rectangle -> 0f
    is DrawShape.RoundedRectangle -> (radius.value * density).coerceIn(0f, min(bounds.width, bounds.height) / 2f)
    DrawShape.Circle, DrawShape.Pill -> min(bounds.width, bounds.height) / 2f
    is DrawShape.CutCorner -> (size.value * density).coerceIn(0f, min(bounds.width, bounds.height) / 2f)
    is DrawShape.RoundedCorners -> maxOf(
        topLeft.value * density,
        topRight.value * density,
        bottomRight.value * density,
        bottomLeft.value * density,
    ).coerceIn(0f, min(bounds.width, bounds.height) / 2f)
}

private fun rectanglePath(bounds: Rectangle, fillRule: FillRule): DrawPath = drawPath(fillRule) {
    moveTo(bounds.x, bounds.y)
    lineTo(bounds.x + bounds.width, bounds.y)
    lineTo(bounds.x + bounds.width, bounds.y + bounds.height)
    lineTo(bounds.x, bounds.y + bounds.height)
    close()
}

private fun roundedRectanglePath(bounds: Rectangle, radius: Dp, density: Float, fillRule: FillRule): DrawPath {
    val r = (radius.value * density).coerceIn(0f, min(bounds.width, bounds.height) / 2f)
    if (r == 0f) return rectanglePath(bounds, fillRule)

    val left = bounds.x
    val top = bounds.y
    val right = bounds.x + bounds.width
    val bottom = bounds.y + bounds.height

    return drawPath(fillRule) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(right - 2f * r, top, right, top + 2f * r, -90f, 90f)
        lineTo(right, bottom - r)
        arcTo(right - 2f * r, bottom - 2f * r, right, bottom, 0f, 90f)
        lineTo(left + r, bottom)
        arcTo(left, bottom - 2f * r, left + 2f * r, bottom, 90f, 90f)
        lineTo(left, top + r)
        arcTo(left, top, left + 2f * r, top + 2f * r, 180f, 90f)
        close()
    }
}

private fun roundedCornersPath(
    bounds: Rectangle,
    spec: DrawShape.RoundedCorners,
    density: Float,
    fillRule: FillRule,
): DrawPath {
    val limit = min(bounds.width, bounds.height) / 2f
    val topLeft = (spec.topLeft.value * density).coerceIn(0f, limit)
    val topRight = (spec.topRight.value * density).coerceIn(0f, limit)
    val bottomRight = (spec.bottomRight.value * density).coerceIn(0f, limit)
    val bottomLeft = (spec.bottomLeft.value * density).coerceIn(0f, limit)

    val left = bounds.x
    val top = bounds.y
    val right = bounds.x + bounds.width
    val bottom = bounds.y + bounds.height

    return drawPath(fillRule) {
        moveTo(left + topLeft, top)
        lineTo(right - topRight, top)
        if (topRight > 0f) arcTo(right - 2f * topRight, top, right, top + 2f * topRight, -90f, 90f)
        lineTo(right, bottom - bottomRight)
        if (bottomRight > 0f) {
            arcTo(right - 2f * bottomRight, bottom - 2f * bottomRight, right, bottom, 0f, 90f)
        }
        lineTo(left + bottomLeft, bottom)
        if (bottomLeft > 0f) arcTo(left, bottom - 2f * bottomLeft, left + 2f * bottomLeft, bottom, 90f, 90f)
        lineTo(left, top + topLeft)
        if (topLeft > 0f) arcTo(left, top, left + 2f * topLeft, top + 2f * topLeft, 180f, 90f)
        close()
    }
}

private fun circlePath(bounds: Rectangle, fillRule: FillRule): DrawPath {
    val diameter = min(bounds.width, bounds.height)
    val insetX = (bounds.width - diameter) / 2f
    val insetY = (bounds.height - diameter) / 2f
    val r = diameter / 2f
    val left = bounds.x + insetX
    val top = bounds.y + insetY
    val right = left + diameter
    val bottom = top + diameter
    return drawPath(fillRule) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(right - 2f * r, top, right, top + 2f * r, -90f, 90f)
        lineTo(right, bottom - r)
        arcTo(right - 2f * r, bottom - 2f * r, right, bottom, 0f, 90f)
        lineTo(left + r, bottom)
        arcTo(left, bottom - 2f * r, left + 2f * r, bottom, 90f, 90f)
        lineTo(left, top + r)
        arcTo(left, top, left + 2f * r, top + 2f * r, 180f, 90f)
        close()
    }
}

private fun pillPath(bounds: Rectangle, fillRule: FillRule): DrawPath {
    val r = min(bounds.width, bounds.height) / 2f
    val left = bounds.x
    val top = bounds.y
    val right = bounds.x + bounds.width
    val bottom = bounds.y + bounds.height
    return drawPath(fillRule) {
        moveTo(left + r, top)
        lineTo(right - r, top)
        arcTo(right - 2f * r, top, right, top + 2f * r, -90f, 90f)
        lineTo(right, bottom - r)
        arcTo(right - 2f * r, bottom - 2f * r, right, bottom, 0f, 90f)
        lineTo(left + r, bottom)
        arcTo(left, bottom - 2f * r, left + 2f * r, bottom, 90f, 90f)
        lineTo(left, top + r)
        arcTo(left, top, left + 2f * r, top + 2f * r, 180f, 90f)
        close()
    }
}

private fun cutCornerPath(bounds: Rectangle, size: Dp, density: Float, fillRule: FillRule): DrawPath {
    val cut = (size.value * density).coerceIn(0f, min(bounds.width, bounds.height) / 2f)
    if (cut == 0f) return rectanglePath(bounds, fillRule)

    val left = bounds.x
    val top = bounds.y
    val right = bounds.x + bounds.width
    val bottom = bounds.y + bounds.height

    return drawPath(fillRule) {
        moveTo(left + cut, top)
        lineTo(right - cut, top)
        lineTo(right, top + cut)
        lineTo(right, bottom - cut)
        lineTo(right - cut, bottom)
        lineTo(left + cut, bottom)
        lineTo(left, bottom - cut)
        lineTo(left, top + cut)
        close()
    }
}
