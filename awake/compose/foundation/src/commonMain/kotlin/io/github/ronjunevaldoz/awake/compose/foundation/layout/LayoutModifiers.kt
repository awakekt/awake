// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutModifierNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.Density
import io.github.ronjunevaldoz.awake.compose.ui.unit.Dp
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp

fun Modifier.padding(all: Dp): Modifier = this then PaddingNode(all, all, all, all)

fun Modifier.padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): Modifier =
    this then PaddingNode(horizontal, vertical, horizontal, vertical)

fun Modifier.padding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier = this then PaddingNode(start, top, end, bottom)

fun Modifier.width(width: Dp): Modifier = this then SizeNode(width, null)

fun Modifier.height(height: Dp): Modifier = this then SizeNode(null, height)

fun Modifier.size(width: Dp, height: Dp): Modifier = this then SizeNode(width, height)

fun Modifier.size(all: Dp): Modifier = this then SizeNode(all, all)

fun Modifier.fillMaxWidth(): Modifier = this then FillNode(width = true, height = false)

fun Modifier.fillMaxHeight(): Modifier = this then FillNode(width = false, height = true)

fun Modifier.fillMaxSize(): Modifier = this then FillNode(width = true, height = true)

private class PaddingNode(
    private val start: Dp,
    private val top: Dp,
    private val end: Dp,
    private val bottom: Dp,
) : LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val startPx = start.roundToPx()
        val topPx = top.roundToPx()
        val horizontal = startPx + end.roundToPx()
        val vertical = topPx + bottom.roundToPx()
        val placeable = measurable.measure(constraints.offset(-horizontal, -vertical))
        return layout(
            constraints.constrainWidth(placeable.width + horizontal),
            constraints.constrainHeight(placeable.height + vertical),
        ) {
            placeable.placeAt(startPx, topPx)
        }
    }

    // Padding adds to whatever the content asks for. Without these an intrinsic query walks past
    // the link and reports the inner size as if the padding were not there.
    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(height) + start.roundToPx() + end.roundToPx()

    override fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height) + start.roundToPx() + end.roundToPx()

    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(width) + top.roundToPx() + bottom.roundToPx()

    override fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(width) + top.roundToPx() + bottom.roundToPx()

    override fun toString(): String = "padding($start, $top, $end, $bottom)"
}

private class SizeNode(private val width: Dp?, private val height: Dp?) : LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // A requested size is still bounded by what the parent offers -- asking for 500dp inside a
        // 100px parent yields 100, not an overflow.
        val w = width?.roundToPx()?.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = height?.roundToPx()?.coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(
            Constraints.of(
                minWidth = w ?: constraints.minWidth,
                maxWidth = w ?: constraints.maxWidth,
                minHeight = h ?: constraints.minHeight,
                maxHeight = h ?: constraints.maxHeight,
            ),
        )
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    // A fixed size is the answer, whatever the content would have wanted.
    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        width?.roundToPx() ?: measurable.minIntrinsicWidth(height)

    override fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        width?.roundToPx() ?: measurable.maxIntrinsicWidth(height)

    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        height?.roundToPx() ?: measurable.minIntrinsicHeight(width)

    override fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        height?.roundToPx() ?: measurable.maxIntrinsicHeight(width)

    override fun toString(): String = "size($width, $height)"
}

private class FillNode(private val width: Boolean, private val height: Boolean) : LayoutModifierNode {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Compose's rule: filling an unbounded axis is a no-op. Without it the child would adopt
        // whatever sentinel stands in for "no bound" as a real size -- the bug class `ui-core`'s
        // UNBOUNDED_MAIN_AXIS produced and FillMaxUnboundedParentTest guards.
        val minWidth = if (width && constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val minHeight = if (height && constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val placeable = measurable.measure(constraints.copy(minWidth = minWidth, minHeight = minHeight))
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    override fun toString(): String = "fillMax(width=$width, height=$height)"
}
