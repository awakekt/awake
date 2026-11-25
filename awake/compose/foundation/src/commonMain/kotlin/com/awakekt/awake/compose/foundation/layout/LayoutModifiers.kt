/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.layout.IntrinsicMeasurable
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.layout.PlacementScope
import com.awakekt.awake.compose.ui.node.LayoutModifierNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Adds [all] of space on every side of this node's content. */
fun Modifier.padding(all: Dp): Modifier = this then PaddingElement(all, all, all, all)

/** Adds [horizontal] space on the start and end sides and [vertical] space on the top and bottom. */
fun Modifier.padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): Modifier =
    this then PaddingElement(horizontal, vertical, horizontal, vertical)

/** Adds a separate amount of space on each side of this node's content. */
fun Modifier.padding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier = this then PaddingElement(start, top, end, bottom)

/** Requests a fixed [width], still clamped to what the parent allows. */
fun Modifier.width(width: Dp): Modifier = this then SizeElement(width, Dp.Unspecified)

/** Requests a fixed [height], still clamped to what the parent allows. */
fun Modifier.height(height: Dp): Modifier = this then SizeElement(Dp.Unspecified, height)

/** Requests a fixed [width] and [height], each still clamped to what the parent allows. */
fun Modifier.size(width: Dp, height: Dp): Modifier = this then SizeElement(width, height)

/** Requests a fixed square of [all] on each side, still clamped to what the parent allows. */
fun Modifier.size(all: Dp): Modifier = this then SizeElement(all, all)

private val FillMaxWidthFull = FillElement(widthFraction = 1f, heightFraction = Float.NaN)
private val FillMaxHeightFull = FillElement(widthFraction = Float.NaN, heightFraction = 1f)
private val FillMaxSizeFull = FillElement(widthFraction = 1f, heightFraction = 1f)

/** Occupies [fraction] of a bounded parent width; an unbounded width remains untouched. */
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier =
    this then if (fraction == 1f) {
        FillMaxWidthFull
    } else {
        FillElement(widthFraction = fraction.validFillFraction(), heightFraction = Float.NaN)
    }

/** Occupies [fraction] of a bounded parent height; an unbounded height remains untouched. */
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier =
    this then if (fraction == 1f) {
        FillMaxHeightFull
    } else {
        FillElement(widthFraction = Float.NaN, heightFraction = fraction.validFillFraction())
    }

/** Occupies [fraction] of each bounded parent axis. */
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier =
    this then if (fraction == 1f) {
        FillMaxSizeFull
    } else {
        val valid = fraction.validFillFraction()
        FillElement(widthFraction = valid, heightFraction = valid)
    }

private val WrapContentWidthCenter = WrapContentElement(
    wrapWidth = true,
    horizontalAlignment = Alignment.CenterHorizontally,
    unbounded = false,
)
private val WrapContentHeightCenter = WrapContentElement(
    wrapHeight = true,
    verticalAlignment = Alignment.CenterVertically,
    unbounded = false,
)
private val WrapContentSizeCenter = WrapContentElement(
    wrapWidth = true,
    wrapHeight = true,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalAlignment = Alignment.CenterVertically,
    unbounded = false,
)

/** Lets content measure below the incoming minimum width and aligns it in the reported width. */
fun Modifier.wrapContentWidth(
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
    unbounded: Boolean = false,
): Modifier = this then if (align === Alignment.CenterHorizontally && !unbounded) {
    WrapContentWidthCenter
} else {
    WrapContentElement(wrapWidth = true, horizontalAlignment = align, unbounded = unbounded)
}

/** Lets content measure below the incoming minimum height and aligns it in the reported height. */
fun Modifier.wrapContentHeight(
    align: Alignment.Vertical = Alignment.CenterVertically,
    unbounded: Boolean = false,
): Modifier = this then if (align === Alignment.CenterVertically && !unbounded) {
    WrapContentHeightCenter
} else {
    WrapContentElement(wrapHeight = true, verticalAlignment = align, unbounded = unbounded)
}

/** Lets content measure below both incoming minimums and aligns it in the reported size. */
fun Modifier.wrapContentSize(
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    unbounded: Boolean = false,
): Modifier = this then if (horizontalAlignment === Alignment.CenterHorizontally &&
    verticalAlignment === Alignment.CenterVertically &&
    !unbounded
) {
    WrapContentSizeCenter
} else {
    WrapContentElement(
        wrapWidth = true,
        wrapHeight = true,
        horizontalAlignment = horizontalAlignment,
        verticalAlignment = verticalAlignment,
        unbounded = unbounded,
    )
}

private class PaddingElement(
    private val start: Dp,
    private val top: Dp,
    private val end: Dp,
    private val bottom: Dp,
) : ModifierNodeElement<PaddingNode>() {
    override fun create(): PaddingNode = PaddingNode()
    override fun update(node: PaddingNode) {
        node.start = start
        node.top = top
        node.end = end
        node.bottom = bottom
    }
    override fun toString(): String = "padding($start, $top, $end, $bottom)"
}

private class PaddingNode :
    Modifier.Node(),
    LayoutModifierNode {
    var start: Dp = 0.dp
    var top: Dp = 0.dp
    var end: Dp = 0.dp
    var bottom: Dp = 0.dp

    // What the last measure placed, for a placement block made once: a block capturing locals is a
    // new object every measure.
    private var placeable: Placeable? = null
    private var placeX = 0
    private var placeY = 0
    private val place: PlacementScope.() -> Unit = { placeable?.placeAt(placeX, placeY) }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val startPx = start.roundToPx()
        val topPx = top.roundToPx()
        val horizontal = startPx + end.roundToPx()
        val vertical = topPx + bottom.roundToPx()
        val placeable = measurable.measure(constraints.offset(-horizontal, -vertical))
        this@PaddingNode.placeable = placeable
        placeX = startPx
        placeY = topPx
        return layout(
            constraints.constrainWidth(placeable.width + horizontal),
            constraints.constrainHeight(placeable.height + vertical),
            place = place,
        )
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
    private val width: Dp,
    private val height: Dp,
) : ModifierNodeElement<SizeNode>() {
    override fun create(): SizeNode = SizeNode()
    override fun update(node: SizeNode) {
        node.width = width
        node.height = height
    }
    override fun toString(): String = "size(${width.orNullString()}, ${height.orNullString()})"
}

private class SizeNode :
    Modifier.Node(),
    LayoutModifierNode {
    var width: Dp = Dp.Unspecified
    var height: Dp = Dp.Unspecified
    private val place = PlaceAtOrigin()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // A requested size is still bounded by what the parent offers -- asking for 500dp inside a
        // 100px parent yields 100, not an overflow. Plain unboxed Dp + Int avoid boxing on every call.
        val width = width
        val height = height
        val hasWidth = width.isSpecified
        val hasHeight = height.isSpecified
        val w = if (hasWidth) width.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth) else 0
        val h = if (hasHeight) height.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight) else 0
        val placeable = measurable.measure(
            Constraints.of(
                minWidth = if (hasWidth) w else constraints.minWidth,
                maxWidth = if (hasWidth) w else constraints.maxWidth,
                minHeight = if (hasHeight) h else constraints.minHeight,
                maxHeight = if (hasHeight) h else constraints.maxHeight,
            ),
        )
        place.placeable = placeable
        return layout(placeable.width, placeable.height, place = place.block)
    }

    // A fixed size is the answer, whatever the content would have wanted.
    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        if (width.isSpecified) width.roundToPx() else measurable.minIntrinsicWidth(height)

    override fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        if (width.isSpecified) width.roundToPx() else measurable.maxIntrinsicWidth(height)

    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        if (height.isSpecified) height.roundToPx() else measurable.minIntrinsicHeight(width)

    override fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        if (height.isSpecified) height.roundToPx() else measurable.maxIntrinsicHeight(width)

    override fun toString(): String = "size(${width.orNullString()}, ${height.orNullString()})"
}

private fun Dp.orNullString(): String = if (isSpecified) toString() else "null"

private fun Float.validFillFraction(): Float {
    require(this in 0f..1f) { "fill fraction must be between 0 and 1, was $this" }
    return this
}

private class FillElement(
    private val widthFraction: Float,
    private val heightFraction: Float,
) : ModifierNodeElement<FillNode>() {
    override fun create(): FillNode = FillNode()
    override fun update(node: FillNode) {
        node.widthFraction = widthFraction
        node.heightFraction = heightFraction
    }
    override fun toString(): String =
        "fillMax(width=${widthFraction.orNullString()}, height=${heightFraction.orNullString()})"
}

private class FillNode :
    Modifier.Node(),
    LayoutModifierNode {
    var widthFraction: Float = Float.NaN
    var heightFraction: Float = Float.NaN
    private val place = PlaceAtOrigin()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        // Compose's rule: filling an unbounded axis is a no-op. Without it the child would adopt
        // whatever sentinel stands in for "no bound" as a real size -- the bug class `ui-core`'s
        // UNBOUNDED_MAIN_AXIS produced. Plain unboxed Float + Int avoid boxing on every call.
        val widthFraction = widthFraction
        val heightFraction = heightFraction
        val fillsWidth = !widthFraction.isNaN() && constraints.hasBoundedWidth
        val fillsHeight = !heightFraction.isNaN() && constraints.hasBoundedHeight
        val targetWidth = if (fillsWidth) constraints.constrainWidth((constraints.maxWidth * widthFraction).roundToInt()) else 0
        val targetHeight = if (fillsHeight) constraints.constrainHeight((constraints.maxHeight * heightFraction).roundToInt()) else 0
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = if (fillsWidth) targetWidth else constraints.minWidth,
                maxWidth = if (fillsWidth) targetWidth else constraints.maxWidth,
                minHeight = if (fillsHeight) targetHeight else constraints.minHeight,
                maxHeight = if (fillsHeight) targetHeight else constraints.maxHeight,
            ),
        )
        place.placeable = placeable
        return layout(placeable.width, placeable.height, place = place.block)
    }

    override fun toString(): String =
        "fillMax(width=${widthFraction.orNullString()}, height=${heightFraction.orNullString()})"
}

private fun Float.orNullString(): String = if (isNaN()) "null" else toString()

private class WrapContentElement(
    private val wrapWidth: Boolean = false,
    private val wrapHeight: Boolean = false,
    private val horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    private val verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    private val unbounded: Boolean,
) : ModifierNodeElement<WrapContentNode>() {
    override fun create(): WrapContentNode = WrapContentNode()
    override fun update(node: WrapContentNode) {
        node.wrapWidth = wrapWidth
        node.wrapHeight = wrapHeight
        node.horizontalAlignment = horizontalAlignment
        node.verticalAlignment = verticalAlignment
        node.unbounded = unbounded
    }
    override fun toString(): String = "wrapContent(width=$wrapWidth, height=$wrapHeight, unbounded=$unbounded)"
}

private class WrapContentNode :
    Modifier.Node(),
    LayoutModifierNode {
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
        val x = if (wrapWidth) horizontalAlignment.align(placeable.width, width) else 0
        val y = if (wrapHeight) verticalAlignment.align(placeable.height, height) else 0
        return layoutSingle(width, height, placeable, x, y, relative = false)
    }
}

/**
 * Places what a modifier node last measured at its own origin, with one block made per node rather
 * than a block capturing the placeable on every measure.
 */
private class PlaceAtOrigin {
    var placeable: Placeable? = null
    val block: PlacementScope.() -> Unit = { placeable?.placeAt(0, 0) }
}
