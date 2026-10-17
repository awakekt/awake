/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.IntSize
import com.awakekt.awake.compose.ui.unit.dp

context(_: Composer)
/**
 * Places [content] with its [pivot] point on the point ([x], [y]) of this layout's box, moved by
 * ([offsetX], [offsetY]): a label or marker over a point, a tooltip above the pointer.
 *
 * The layout fills the room it is given, so put it where its box is the space the point is measured
 * in, such as over the whole screen. Each child is measured at its own size, up to that room, and
 * placed so that the point of it [pivot] names sits on the point: [Alignment.BottomCenter] stands it
 * on the point, [Alignment.Center] centres it there, and [Alignment.TopStart] hangs it from its
 * top-left corner. A positive [offsetY] moves it down, so a label above a point takes a negative one.
 * A child is never moved to stay in the box: one whose point is near an edge can reach past it.
 *
 * The point and offset are in dp. The point lands on the nearest whole pixel. A centred pivot on a
 * child an odd number of pixels across starts its middle pixel at the point, so the child reaches
 * half a pixel further right, or down, than the exact centre would. In a right-to-left layout a
 * `Start` pivot is the child's right edge.
 *
 * @param x How far the point is from the box's left edge, in dp.
 * @param y How far the point is from the box's top edge, in dp.
 * @param modifier Applied to the layout's own full-size box.
 * @param pivot Which point of each child sits on the point.
 * @param offsetX How far each child is moved right of the point, in dp, or left when negative.
 * @param offsetY How far each child is moved below the point, in dp, or above it when negative.
 * @param content What is placed on the point.
 */
fun PointAnchor(
    x: Dp,
    y: Dp,
    modifier: Modifier = Modifier,
    pivot: Alignment = Alignment.BottomCenter,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    content: context(Composer) () -> Unit,
) {
    Layout(PointAnchorNode, modifier, PointAnchorPolicy(x + offsetX, y + offsetY, pivot), content)
}

/** [PointAnchor]'s node type, so another layout at the same place is not taken for it. */
private object PointAnchorNode

/** Fills the room it is given and puts each child's [pivot] on ([x], [y]). */
private class PointAnchorPolicy(private val x: Dp, private val y: Dp, private val pivot: Alignment) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = Constraints.of(0, constraints.maxWidth, 0, constraints.maxHeight)
        val placeables = measurables.map { it.measure(loose) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.maxOfOrNull { it.height } ?: 0
        val pointX = x.roundToPx()
        val pointY = y.roundToPx()
        val direction = layoutDirection
        return layout(width, height) {
            for (placeable in placeables) {
                // Aligned in no room at all, a pivot answers how far the child's own pivot point is
                // from its top-left, negated: BottomCenter gives (-width / 2, -height).
                val shift = pivot.align(IntSize(placeable.width, placeable.height), IntSize.Zero, direction)
                placeable.placeAbsoluteAt(pointX + shift.x, pointY + shift.y)
            }
        }
    }
}
