/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.layout.IntrinsicMeasurable
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.node.LayoutModifierNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.compose.ui.unit.Dp
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Moves the content without changing what the parent measured.
 *
 * The node reports its unmoved size, so an offset never re-lays out a sibling -- which is the whole
 * difference from `padding`, and the reason a badge nudged 2 dp does not shift the row it sits in.
 *
 * Compose's `absoluteOffset` -- the one that ignores layout direction -- is deliberately absent
 * rather than aliased to this. `14-density-resize.md` has not settled RTL, so the two would be
 * identical today and one of them would silently start behaving differently the day it is. Absent
 * beats inert; see `11-refinements.md`.
 */
fun Modifier.offset(x: Dp = 0.dp, y: Dp = 0.dp): Modifier = this then OffsetElement(x, y)

/**
 * Sizes the content to [ratio] (width ÷ height), fitting inside the incoming constraints.
 *
 * Tries width-first, as Compose does: with a bounded width the height follows from it. That order
 * matters for the common case of a media card in a column, where the width is dictated and the
 * height should follow rather than the other way round.
 */
fun Modifier.aspectRatio(ratio: Float): Modifier {
    require(ratio > 0f) { "ratio must be positive, was $ratio" }
    return this then AspectRatioElement(ratio)
}

/** Bounds the content's width without fixing it. `Dp.Unspecified` leaves that end open. */
fun Modifier.widthIn(min: Dp = Dp.Unspecified, max: Dp = Dp.Unspecified): Modifier =
    this then SizeInElement(min, max, Dp.Unspecified, Dp.Unspecified)

fun Modifier.heightIn(min: Dp = Dp.Unspecified, max: Dp = Dp.Unspecified): Modifier =
    this then SizeInElement(Dp.Unspecified, Dp.Unspecified, min, max)

fun Modifier.sizeIn(
    minWidth: Dp = Dp.Unspecified,
    minHeight: Dp = Dp.Unspecified,
    maxWidth: Dp = Dp.Unspecified,
    maxHeight: Dp = Dp.Unspecified,
): Modifier = this then SizeInElement(minWidth, maxWidth, minHeight, maxHeight)

/**
 * A size the parent's constraints cannot override.
 *
 * `size` clamps into what the parent offers; this does not, so the content may overflow. That is the
 * point: an icon asked for 24 dp is 24 dp even in a 16 dp slot, because a silently shrunk icon looks
 * like a rendering bug rather than a layout one.
 */
fun Modifier.requiredWidth(width: Dp): Modifier = this then RequiredSizeElement(width, null)

fun Modifier.requiredHeight(height: Dp): Modifier = this then RequiredSizeElement(null, height)

fun Modifier.requiredSize(size: Dp): Modifier = this then RequiredSizeElement(size, size)

fun Modifier.requiredSize(width: Dp, height: Dp): Modifier = this then RequiredSizeElement(width, height)

/**
 * A floor the content may exceed but not fall below.
 *
 * Unlike `sizeIn`'s min, this yields to a size the content was *already* given: it only applies
 * where the incoming minimum is zero, which is what makes it usable as a component's own default
 * without overriding what a caller asked for.
 */
fun Modifier.defaultMinSize(minWidth: Dp = Dp.Unspecified, minHeight: Dp = Dp.Unspecified): Modifier =
    this then DefaultMinSizeElement(minWidth, minHeight)

private class OffsetElement(
    private val x: Dp,
    private val y: Dp,
) : ModifierNodeElement<OffsetNode>() {
    override fun create(): OffsetNode = OffsetNode()
    override fun update(node: OffsetNode) {
        node.x = x
        node.y = y
    }
}

private class OffsetNode :
    Modifier.Node(),
    LayoutModifierNode {
    var x: Dp = 0.dp
    var y: Dp = 0.dp
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeRelativeAt(x.roundToPx(), y.roundToPx())
        }
    }

    override fun toString(): String = "offset($x, $y)"
}

/**
 * Moves the content by fixed physical coordinates without mirroring in right-to-left layout.
 */
fun Modifier.absoluteOffset(x: Dp = 0.dp, y: Dp = 0.dp): Modifier = this then AbsoluteOffsetElement(x, y)

private class AbsoluteOffsetElement(
    private val x: Dp,
    private val y: Dp,
) : ModifierNodeElement<AbsoluteOffsetNode>() {
    override fun create(): AbsoluteOffsetNode = AbsoluteOffsetNode()
    override fun update(node: AbsoluteOffsetNode) {
        node.x = x
        node.y = y
    }
}

private class AbsoluteOffsetNode :
    Modifier.Node(),
    LayoutModifierNode {
    var x: Dp = 0.dp
    var y: Dp = 0.dp
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeAbsoluteAt(x.roundToPx(), y.roundToPx())
        }
    }

    override fun toString(): String = "absoluteOffset($x, $y)"
}

private class AspectRatioElement(
    private val ratio: Float,
) : ModifierNodeElement<AspectRatioNode>() {
    override fun create(): AspectRatioNode = AspectRatioNode()
    override fun update(node: AspectRatioNode) {
        node.ratio = ratio
    }
}

private class AspectRatioNode :
    Modifier.Node(),
    LayoutModifierNode {
    var ratio: Float = 1f
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val size = resolve(constraints)
        val placeable = measurable.measure(
            if (size == null) constraints else Constraints.of(size.first, size.first, size.second, size.second),
        )
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    /** Null when neither axis is bounded -- there is no ratio to apply to an unbounded box. */
    private fun resolve(constraints: Constraints): Pair<Int, Int>? = when {
        constraints.hasBoundedWidth -> {
            val w = constraints.maxWidth
            w to (w / ratio).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        }
        constraints.hasBoundedHeight -> {
            val h = constraints.maxHeight
            (h * ratio).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth) to h
        }
        else -> null
    }

    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        (height * ratio).roundToInt()

    override fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        (height * ratio).roundToInt()

    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        (width / ratio).roundToInt()

    override fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        (width / ratio).roundToInt()

    override fun toString(): String = "aspectRatio($ratio)"
}

private class SizeInElement(
    private val minWidth: Dp,
    private val maxWidth: Dp,
    private val minHeight: Dp,
    private val maxHeight: Dp,
) : ModifierNodeElement<SizeInNode>() {
    override fun create(): SizeInNode = SizeInNode()
    override fun update(node: SizeInNode) {
        node.minWidth = minWidth
        node.maxWidth = maxWidth
        node.minHeight = minHeight
        node.maxHeight = maxHeight
    }
}

private class SizeInNode :
    Modifier.Node(),
    LayoutModifierNode {
    var minWidth: Dp = Dp.Unspecified
    var maxWidth: Dp = Dp.Unspecified
    var minHeight: Dp = Dp.Unspecified
    var maxHeight: Dp = Dp.Unspecified
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        // Narrowed into the incoming range, never outside it: a `widthIn(max = 500.dp)` inside a
        // 100 px parent must not hand the child a 500 px ceiling it can actually take.
        val lowW = minWidth.orElse(constraints.minWidth).coerceIn(constraints.minWidth, constraints.maxWidth)
        val highW = maxWidth.orElse(constraints.maxWidth).coerceIn(lowW, constraints.maxWidth)
        val lowH = minHeight.orElse(constraints.minHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        val highH = maxHeight.orElse(constraints.maxHeight).coerceIn(lowH, constraints.maxHeight)
        val placeable = measurable.measure(Constraints.of(lowW, highW, lowH, highH))
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    context(density: Density)
    private fun Dp.orElse(fallback: Int): Int = if (isSpecified) with(density) { roundToPx() } else fallback

    // Without these an intrinsic query walks straight past this link and reports the content's own
    // size, as if the bounds were not here. A dropdown asking for its widest item got the item and
    // never the `min-w-[8rem]` floor sitting right next to it.
    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.minIntrinsicWidth(height).coerceInBounds(minWidth, maxWidth)

    override fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height).coerceInBounds(minWidth, maxWidth)

    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.minIntrinsicHeight(width).coerceInBounds(minHeight, maxHeight)

    override fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        measurable.maxIntrinsicHeight(width).coerceInBounds(minHeight, maxHeight)

    /** An unspecified bound leaves that end alone, matching what [measure] does with it. */
    context(density: Density)
    private fun Int.coerceInBounds(min: Dp, max: Dp): Int = with(density) {
        val low = if (min.isSpecified) maxOf(this@coerceInBounds, min.roundToPx()) else this@coerceInBounds
        if (max.isSpecified) minOf(low, maxOf(max.roundToPx(), 0)) else low
    }

    override fun toString(): String = "sizeIn($minWidth..$maxWidth, $minHeight..$maxHeight)"
}

private class RequiredSizeElement(
    private val width: Dp?,
    private val height: Dp?,
) : ModifierNodeElement<RequiredSizeNode>() {
    override fun create(): RequiredSizeNode = RequiredSizeNode()
    override fun update(node: RequiredSizeNode) {
        node.width = width
        node.height = height
    }
}

private class RequiredSizeNode :
    Modifier.Node(),
    LayoutModifierNode {
    var width: Dp? = null
    var height: Dp? = null
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val w = width?.roundToPx()
        val h = height?.roundToPx()
        val placeable = measurable.measure(
            Constraints.of(
                minWidth = w ?: constraints.minWidth,
                maxWidth = w ?: constraints.maxWidth,
                minHeight = h ?: constraints.minHeight,
                maxHeight = h ?: constraints.maxHeight,
            ),
        )
        // Reported unclamped, which is the difference from `size`. The parent sees the real size and
        // can overflow rather than the content being silently shrunk into the slot.
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    override fun Density.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        width?.roundToPx() ?: measurable.minIntrinsicWidth(height)

    override fun Density.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        width?.roundToPx() ?: measurable.maxIntrinsicWidth(height)

    override fun Density.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        height?.roundToPx() ?: measurable.minIntrinsicHeight(width)

    override fun Density.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
        height?.roundToPx() ?: measurable.maxIntrinsicHeight(width)

    override fun toString(): String = "requiredSize($width, $height)"
}

private class DefaultMinSizeElement(
    private val minWidth: Dp,
    private val minHeight: Dp,
) : ModifierNodeElement<DefaultMinSizeNode>() {
    override fun create(): DefaultMinSizeNode = DefaultMinSizeNode()
    override fun update(node: DefaultMinSizeNode) {
        node.minWidth = minWidth
        node.minHeight = minHeight
    }
}

private class DefaultMinSizeNode :
    Modifier.Node(),
    LayoutModifierNode {
    var minWidth: Dp = Dp.Unspecified
    var minHeight: Dp = Dp.Unspecified
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        // Only where the caller asked for nothing. A non-zero incoming minimum is someone's explicit
        // choice, and a default that overrode it would not be a default.
        val minW = if (constraints.minWidth == 0 && minWidth.isSpecified) {
            minWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        } else {
            constraints.minWidth
        }
        val minH = if (constraints.minHeight == 0 && minHeight.isSpecified) {
            minHeight.roundToPx().coerceAtMost(constraints.maxHeight)
        } else {
            constraints.minHeight
        }
        val placeable = measurable.measure(constraints.copy(minWidth = minW, minHeight = minH))
        return layout(placeable.width, placeable.height) { placeable.placeAt(0, 0) }
    }

    override fun toString(): String = "defaultMinSize($minWidth, $minHeight)"
}
