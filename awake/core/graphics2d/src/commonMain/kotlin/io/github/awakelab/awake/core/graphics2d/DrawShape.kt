/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.graphics2d

import io.github.awakelab.awake.core.math2d.Dp
import io.github.awakelab.awake.core.math2d.Rectangle
import kotlin.math.min

sealed interface DrawShape {
    data object Rectangle : DrawShape
    data class RoundedRectangle(val radius: Dp) : DrawShape
    data object Circle : DrawShape
    data object Pill : DrawShape
    data class CutCorner(val size: Dp) : DrawShape

    /**
     * A rectangle whose corners round independently -- Compose's `RoundedCornerShape(topStart,
     * topEnd, bottomEnd, bottomStart)`.
     */
    data class RoundedCorners(
        val topLeft: Dp,
        val topRight: Dp,
        val bottomRight: Dp,
        val bottomLeft: Dp,
    ) : DrawShape

    companion object {
        val Rectangle: DrawShape.Rectangle get() = DrawShape.Rectangle
        fun RoundedRectangle(radius: Dp): DrawShape.RoundedRectangle = DrawShape.RoundedRectangle(radius)
        val Circle: DrawShape.Circle get() = DrawShape.Circle
        val Pill: DrawShape.Pill get() = DrawShape.Pill
        fun CutCorner(size: Dp): DrawShape.CutCorner = DrawShape.CutCorner(size)
        fun RoundedCorners(
            topLeft: Dp,
            topRight: Dp,
            bottomRight: Dp,
            bottomLeft: Dp,
        ): DrawShape.RoundedCorners = DrawShape.RoundedCorners(topLeft, topRight, bottomRight, bottomLeft)
    }
}

typealias UiShapeSpec = DrawShape

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
