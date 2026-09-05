/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.draw.drawWithContent
import io.github.awakelab.awake.compose.ui.graphics.RoundedCornerShape
import io.github.awakelab.awake.compose.ui.graphics.Shape
import io.github.awakelab.awake.compose.ui.graphics.ShapeOutline
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.DrawShape
import io.github.awakelab.awake.core.graphics2d.DrawStroke
import io.github.awakelab.awake.core.graphics2d.StrokeJoin
import io.github.awakelab.awake.core.graphics2d.toPath
import io.github.awakelab.awake.core.math2d.Rectangle
import io.github.awakelab.awake.core.math2d.Size2D
import io.github.awakelab.awake.core.math2d.Dp as StrokeWidth

/** `focus-visible:ring-[3px]`. */
internal val FieldFocusRingWidth: Dp = 3.dp

/**
 * A focus ring that follows the field's own [Shape], corner by corner.
 *
 * The uniform-radius version below cannot describe a shape whose corners differ, and an OTP slot is
 * exactly that: the first slot is rounded on its leading side and square where it meets its
 * neighbour. Given one radius the ring came out fully rounded and the slot read as a detached box
 * floating above the row rather than a highlighted member of it.
 *
 * The ring sits *outside* the border box, as `ring` does in CSS, so its radius is the field's plus
 * half the stroke -- which is what the negative `radiusInset` asks `createOutline` for.
 */
internal fun Modifier.focusRing(
    alpha: Float,
    width: Dp,
    color: Color,
    shape: Shape,
): Modifier = if (alpha <= 0f || width.value <= 0f) {
    this
} else {
    drawWithContent {
        drawContent()

        val ringStroke = width.value * density
        val ringHalf = ringStroke / 2f
        val ringBounds = Rectangle(
            -ringHalf,
            -ringHalf,
            this.width.toFloat() + ringStroke,
            this.height.toFloat() + ringStroke,
        )
        drawStrokedPath(
            shape.ringPath(ringBounds, density, ringHalf),
            DrawStroke(width = StrokeWidth(ringStroke), join = StrokeJoin.Round),
            color.withAlpha(color.a * alpha),
        )
    }
}

/** Draws a focus ring without changing the field's measured size or content inset. */
internal fun Modifier.focusRing(
    alpha: Float,
    width: Dp,
    color: Color,
    cornerRadius: Dp,
): Modifier = if (alpha <= 0f || width.value <= 0f) {
    this
} else {
    drawWithContent {
        drawContent()

        val ringStroke = width.value * density
        val ringHalf = ringStroke / 2f
        val ringBounds = Rectangle(
            -ringHalf,
            -ringHalf,
            this.width.toFloat() + ringStroke,
            this.height.toFloat() + ringStroke,
        )
        val ringRadius = cornerRadius.value * density + ringHalf
        drawStrokedPath(
            DrawShape.RoundedRectangle(StrokeWidth(ringRadius)).toPath(ringBounds),
            DrawStroke(width = StrokeWidth(ringStroke), join = StrokeJoin.Round),
            color.withAlpha(color.a * alpha),
        )
    }
}

/**
 * The ring's outline at [bounds], with every corner grown by [ringHalf].
 *
 * Built here rather than reusing the border's own builder, which is `internal` to foundation. A
 * negative `radiusInset` is what grows a radius: the ring wraps the box, so its corners are the
 * box's plus half the stroke, or a rounded box would get a ring that cut across its corners.
 */
private fun Shape.ringPath(
    bounds: Rectangle,
    density: Float,
    ringHalf: Float,
): io.github.awakelab.awake.core.graphics2d.DrawPath {
    val outline = if (this is RoundedCornerShape) {
        createOutline(bounds, density, radiusInset = -ringHalf)
    } else {
        createOutline(Size2D(bounds.width, bounds.height), density)
    }
    return when (outline) {
        is ShapeOutline.Rectangle -> DrawShape.Rectangle.toPath(bounds)
        is ShapeOutline.Rounded -> DrawShape.RoundedRectangle(StrokeWidth(outline.radius)).toPath(bounds)
        is ShapeOutline.Generic -> outline.path
    }
}
