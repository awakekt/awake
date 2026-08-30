/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.layout

import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.awakelab.awake.compose.ui.layout.Measurable
import io.github.awakelab.awake.compose.ui.layout.MeasureResult
import io.github.awakelab.awake.compose.ui.layout.MeasureScope
import io.github.awakelab.awake.compose.ui.node.LayoutModifierNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.Density
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.math.roundToInt

fun Modifier.padding(all: Dp): Modifier = this then PaddingElement(all, all, all, all)

fun Modifier.padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): Modifier =
    this then PaddingElement(horizontal, vertical, horizontal, vertical)

fun Modifier.padding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier = this then PaddingElement(start, top, end, bottom)

fun Modifier.width(width: Dp): Modifier = this then SizeElement(width, null)

fun Modifier.height(height: Dp): Modifier = this then SizeElement(null, height)

fun Modifier.size(width: Dp, height: Dp): Modifier = this then SizeElement(width, height)

fun Modifier.size(all: Dp): Modifier = this then SizeElement(all, all)

/** Occupies [fraction] of a bounded parent width; an unbounded width remains untouched. */
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier =
    this then FillElement(widthFraction = fraction.validFillFraction(), heightFraction = null)

/** Occupies [fraction] of a bounded parent height; an unbounded height remains untouched. */
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier =
    this then FillElement(widthFraction = null, heightFraction = fraction.validFillFraction())

/** Occupies [fraction] of each bounded parent axis. */
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier = this then FillElement(
    widthFraction = fraction.validFillFraction(),
    heightFraction = fraction.validFillFraction(),
)

/** Lets content measure below the incoming minimum width and aligns it in the reported width. */
fun Modifier.wrapContentWidth(
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
    unbounded: Boolean = false,
): Modifier = this then WrapContentElement(wrapWidth = true, horizontalAlignment = align, unbounded = unbounded)

/** Lets content measure below the incoming minimum height and aligns it in the reported height. */
fun Modifier.wrapContentHeight(
    align: Alignment.Vertical = Alignment.CenterVertically,
    unbounded: Boolean = false,
): Modifier = this then WrapContentElement(wrapHeight = true, verticalAlignment = align, unbounded = unbounded)

/** Lets content measure below both incoming minimums and aligns it in the reported size. */
fun Modifier.wrapContentSize(
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    unbounded: Boolean = false,
): Modifier = this then WrapContentElement(
    wrapWidth = true,
    wrapHeight = true,
    horizontalAlignment = horizontalAlignment,
    verticalAlignment = verticalAlignment,
    unbounded = unbounded,
)

private class PaddingElement(
    private val start: Dp,
    private val top: Dp,
    private val end: Dp,
    private val bottom: Dp,
) : ModifierNodeElement<PaddingNode>() {
    override fun create(): PaddingNode = PaddingNode()
    override fun update(node: PaddingNode) {
        node.start = start; node.top = top; node.end = end; node.bottom = bottom
    }
    override fun toString(): String = "padding($start, $top, $end, $bottom)"
}

private class PaddingNode : Modifier.Node(), LayoutModifierNode {
    var start: Dp = 0.dp
    var top: Dp = 0.dp
    var end: Dp = 0.dp
    var bottom: Dp = 0.dp
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

private class SizeElement(
    private val width: Dp?,
    private val height: Dp?,
) : ModifierNodeElement<SizeNode>() {
    override fun create(): SizeNode = SizeNode()
    override fun update(node: SizeNode) { node.width = width; node.height = height }
    override fun toString(): String = "size($width, $height)"
}

private class SizeNode : Modifier.Node(), LayoutModifierNode {
    var width: Dp? = null
    var height: Dp? = null
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

private fun Float.validFillFraction(): Float {
    require(this in 0f..1f) { "fill fraction must be between 0 and 1, was $this" }
    return this
}

private class FillElement(
    private val widthFraction: Float?,
    private val heightFraction: Float?,
) : ModifierNodeElement<FillNode>() {
    override fun create(): FillNode = FillNode()
    override fun update(node: FillNode) { node.widthFraction = widthFraction; node.heightFraction = heightFraction }
    override fun toString(): String = "fillMax(width=$widthFraction, height=$heightFraction)"
}

private class FillNode : Modifier.Node(), LayoutModifierNode {
    var widthFraction: Float? = null
    var heightFraction: Float? = null
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Compose's rule: filling an unbounded axis is a no-op. Without it the child would adopt
        // whatever sentinel stands in for "no bound" as a real size -- the bug class `ui-core`'s
        // UNBOUNDED_MAIN_AXIS produced and FillMaxUnboundedParentTest guards.
        val targetWidth = widthFraction?.takeIf { constraints.hasBoundedWidth }
            ?.let { constraints.constrainWidth((constraints.maxWidth * it).roundToInt()) }
        val targetHeight = heightFraction?.takeIf { constraints.hasBoundedHeight }
            ?.let { constraints.constrainHeight((constraints.maxHeight * it).roundToInt()) }
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = targetWidth ?: constraints.minWidth,
                maxWidth = targetWidth ?: constraints.maxWidth,
                minHeight = targetHeight ?: constraints.minHeight,
                maxHeight = targetHeight ?: constraints.maxHeight,
            ),
        )
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    override fun toString(): String = "fillMax(width=$widthFraction, height=$heightFraction)"
}

private class WrapContentElement(
    private val wrapWidth: Boolean = false,
    private val wrapHeight: Boolean = false,
    private val horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    private val verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    private val unbounded: Boolean,
) : ModifierNodeElement<WrapContentNode>() {
    override fun create(): WrapContentNode = WrapContentNode()
    override fun update(node: WrapContentNode) {
        node.wrapWidth = wrapWidth; node.wrapHeight = wrapHeight
        node.horizontalAlignment = horizontalAlignment; node.verticalAlignment = verticalAlignment
        node.unbounded = unbounded
    }
    override fun toString(): String = "wrapContent(width=$wrapWidth, height=$wrapHeight, unbounded=$unbounded)"
}

private class WrapContentNode : Modifier.Node(), LayoutModifierNode {
    var wrapWidth: Boolean = false
    var wrapHeight: Boolean = false
    var horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally
    var verticalAlignment: Alignment.Vertical = Alignment.CenterVertically
    var unbounded: Boolean = false
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val childConstraints = constraints.copy(
            minWidth = if (wrapWidth) 0 else constraints.minWidth,
            maxWidth = if (wrapWidth && unbounded) Constraints.Infinity else constraints.maxWidth,
            minHeight = if (wrapHeight) 0 else constraints.minHeight,
            maxHeight = if (wrapHeight && unbounded) Constraints.Infinity else constraints.maxHeight,
        )
        val placeable = measurable.measure(childConstraints)
        val width = constraints.constrainWidth(placeable.width)
        val height = constraints.constrainHeight(placeable.height)
        return layout(width, height) {
            placeable.placeAt(
                if (wrapWidth) horizontalAlignment.align(placeable.width, width) else 0,
                if (wrapHeight) verticalAlignment.align(placeable.height, height) else 0,
            )
        }
    }
}
