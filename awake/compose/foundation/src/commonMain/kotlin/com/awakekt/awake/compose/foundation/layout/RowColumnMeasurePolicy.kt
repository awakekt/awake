/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.layout.AlignmentLine
import com.awakekt.awake.compose.ui.layout.FirstBaseline
import com.awakekt.awake.compose.ui.layout.HorizontalAlignmentLine
import com.awakekt.awake.compose.ui.layout.IntrinsicMeasurable
import com.awakekt.awake.compose.ui.layout.LastBaseline
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.layout.VerticalAlignmentLine
import com.awakekt.awake.compose.ui.node.ParentDataModifierNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The axis a Row or Column stacks along, and the mapping from that axis to width/height.
 *
 * The mapping lives here rather than in the policy so there is exactly one place that knows which
 * physical dimension is "main" -- the split that drifted in `ui-core`.
 */
internal enum class LayoutOrientation {
    Horizontal,
    Vertical,
    ;

    fun mainAxisMax(constraints: Constraints): Int =
        if (this == Vertical) constraints.maxHeight else constraints.maxWidth

    fun crossAxisMax(constraints: Constraints): Int =
        if (this == Vertical) constraints.maxWidth else constraints.maxHeight

    fun hasBoundedMainAxis(constraints: Constraints): Boolean =
        if (this == Vertical) constraints.hasBoundedHeight else constraints.hasBoundedWidth

    fun mainAxisSize(placeable: Placeable): Int =
        if (this == Vertical) placeable.height else placeable.width

    fun crossAxisSize(placeable: Placeable): Int =
        if (this == Vertical) placeable.width else placeable.height

    fun constraintsFor(mainMin: Int, mainMax: Int, crossMin: Int, crossMax: Int): Constraints =
        if (this == Vertical) {
            Constraints.of(crossMin, crossMax, mainMin, mainMax)
        } else {
            Constraints.of(mainMin, mainMax, crossMin, crossMax)
        }
}

/** Where a child sits on the cross axis: `(childSize, containerSize) -> offset`. */
internal typealias CrossAxisAlign = (Int, Int) -> Int

internal class RowColumnParentData(
    var weight: Float = 0f,
    var fill: Boolean = true,
    var crossAxisAlign: CrossAxisAlign? = null,
    var alignmentLine: AlignmentLine? = null,
    var flex: FlexItemData? = null,
)

internal class LayoutWeightElement(
    private val weight: Float,
    private val fill: Boolean,
) : ModifierNodeElement<LayoutWeightNode>() {
    override fun create(): LayoutWeightNode = LayoutWeightNode(weight, fill)
    override fun update(node: LayoutWeightNode) {
        node.weight = weight
        node.fill = fill
    }
    override fun toString(): String = "weight($weight, fill=$fill)"
}

internal class LayoutWeightNode(
    var weight: Float,
    var fill: Boolean,
) : Modifier.Node(),
    ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any =
        ((current as? RowColumnParentData) ?: RowColumnParentData()).also {
            it.weight = weight
            it.fill = fill
        }
    override fun toString(): String = "weight($weight, fill=$fill)"
}

internal class VerticalAlignElement(
    private val alignment: Alignment.Vertical,
) : ModifierNodeElement<VerticalAlignNode>() {
    override fun create(): VerticalAlignNode = VerticalAlignNode(alignment)
    override fun update(node: VerticalAlignNode) {
        node.alignment = alignment
    }
}

internal class VerticalAlignNode(
    var alignment: Alignment.Vertical,
) : Modifier.Node(),
    ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any =
        ((current as? RowColumnParentData) ?: RowColumnParentData()).also {
            it.crossAxisAlign = alignment::align
        }
}

internal class HorizontalAlignElement(
    private val alignment: Alignment.Horizontal,
) : ModifierNodeElement<HorizontalAlignNode>() {
    override fun create(): HorizontalAlignNode = HorizontalAlignNode(alignment)
    override fun update(node: HorizontalAlignNode) {
        node.alignment = alignment
    }
}

internal class HorizontalAlignNode(
    var alignment: Alignment.Horizontal,
) : Modifier.Node(),
    ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any =
        ((current as? RowColumnParentData) ?: RowColumnParentData()).also {
            it.crossAxisAlign = alignment::align
        }
}

internal class AlignmentLineElement(
    private val alignmentLine: AlignmentLine,
) : ModifierNodeElement<AlignmentLineNode>() {
    override fun create(): AlignmentLineNode = AlignmentLineNode(alignmentLine)
    override fun update(node: AlignmentLineNode) {
        node.alignmentLine = alignmentLine
    }
}

internal class AlignmentLineNode(
    var alignmentLine: AlignmentLine,
) : Modifier.Node(),
    ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any =
        ((current as? RowColumnParentData) ?: RowColumnParentData()).also {
            it.alignmentLine = alignmentLine
        }
}

internal fun Measurable.rowColumnParentData(): RowColumnParentData? = parentData as? RowColumnParentData

/**
 * Stacks children along one axis, giving weighted children the space the rest did not take.
 *
 * Row and Column share this rather than each owning a transposed copy. `ui-core` split exactly this
 * logic per container and its own `Layout.kt` records the result: "A rule written once per container
 * is a rule that drifts -- the row/column split of exactly this logic is what hid a weighted child
 * collapsing to its content height."
 *
 * Every child is measured **exactly once**. Weighted children are measured after the unweighted
 * ones only because their constraint is unknown until the rest have reported.
 */
internal class RowColumnMeasurePolicy(
    private val orientation: LayoutOrientation,
    private val spacing: Dp,
    private val crossAxisAlign: CrossAxisAlign,
    /** Where leftover main-axis space goes: `(free, count) -> before the first child`. */
    private val leading: (Int, Int) -> Int = { _, _ -> 0 },
    /** Extra space between each pair, on top of [spacing]. */
    private val between: (Int, Int) -> Int = { _, _ -> 0 },
) : MeasurePolicy {

    private val isVertical = orientation == LayoutOrientation.Vertical

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val count = measurables.size
        val placeables = arrayOfNulls<Placeable>(count)
        val gap = spacing.roundToPx()
        val totalGap = gap * (count - 1).coerceAtLeast(0)

        val unweightedMain = measureUnweighted(measurables, placeables, constraints, gap)
        val totalWeight = totalWeightOf(measurables)
        val weightedMain = if (totalWeight > 0f) {
            measureWeighted(measurables, placeables, constraints, unweightedMain + totalGap, totalWeight)
        } else {
            0
        }

        val main = unweightedMain + weightedMain + totalGap

        var maxBeforeLine = 0
        var maxAfterLine = 0
        var maxNonAlignedCross = 0
        for (i in measurables.indices) {
            val placeable = placeables[i] ?: continue
            val data = measurables[i].rowColumnParentData()
            val line = data?.alignmentLine
            val linePos = if (line != null) placeable[line] else AlignmentLine.Unspecified
            if (linePos != AlignmentLine.Unspecified) {
                maxBeforeLine = maxOf(maxBeforeLine, linePos)
                maxAfterLine = maxOf(maxAfterLine, orientation.crossAxisSize(placeable) - linePos)
            } else {
                maxNonAlignedCross = maxOf(maxNonAlignedCross, orientation.crossAxisSize(placeable))
            }
        }
        val cross = maxOf(maxNonAlignedCross, maxBeforeLine + maxAfterLine)

        val width = constraints.constrainWidth(if (isVertical) cross else main)
        val height = constraints.constrainHeight(if (isVertical) main else cross)
        val crossSpace = if (isVertical) width else height

        // The leftover only exists when the parent gave us more than we used, which is what every
        // distributing arrangement divides up. A packing one leaves it alone.
        val mainSpace = if (isVertical) height else width
        val free = (mainSpace - main).coerceAtLeast(0)
        val extra = between(free, count)

        val alignmentLines = if (!isVertical && maxBeforeLine > 0) {
            mapOf<AlignmentLine, Int>(FirstBaseline to maxBeforeLine, LastBaseline to maxBeforeLine)
        } else {
            emptyMap()
        }

        return layout(width, height, alignmentLines) {
            var cursor = leading(free, count)
            for (i in placeables.indices) {
                val placeable = placeables[i] ?: continue
                val data = measurables[i].rowColumnParentData()
                val line = data?.alignmentLine
                val linePos = if (line != null) placeable[line] else AlignmentLine.Unspecified
                val crossOffset = if (linePos != AlignmentLine.Unspecified) {
                    maxBeforeLine - linePos
                } else if (data?.crossAxisAlign != null) {
                    data.crossAxisAlign!!(orientation.crossAxisSize(placeable), crossSpace)
                } else {
                    crossAxisAlign(orientation.crossAxisSize(placeable), crossSpace)
                }
                if (isVertical) {
                    placeable.placeRelativeAt(crossOffset, cursor)
                } else {
                    placeable.placeRelativeAt(cursor, crossOffset)
                }
                cursor += orientation.mainAxisSize(placeable) + gap + extra
            }
        }
    }

    // The main axis sums; the cross axis maxes. The inherited Box-shaped defaults would max both,
    // which reads as a Column claiming it only needs its tallest child's height.
    override fun Density.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        if (isVertical) {
            measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0
        } else {
            sumWithGaps(measurables, spacing) { it.minIntrinsicWidth(height) }
        }

    override fun Density.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        if (isVertical) {
            measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0
        } else {
            sumWithGaps(measurables, spacing) { it.maxIntrinsicWidth(height) }
        }

    override fun Density.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        if (isVertical) {
            sumWithGaps(measurables, spacing) { it.minIntrinsicHeight(width) }
        } else {
            measurables.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0
        }

    override fun Density.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        if (isVertical) {
            sumWithGaps(measurables, spacing) { it.maxIntrinsicHeight(width) }
        } else {
            measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        }

    /** Measures every unweighted child against a loosened main axis, returning their total. */
    private fun measureUnweighted(
        measurables: List<Measurable>,
        placeables: Array<Placeable?>,
        constraints: Constraints,
        gap: Int,
    ): Int {
        var total = 0
        for (i in measurables.indices) {
            if (measurables[i].rowColumnParentData()?.weight?.let { it > 0f } == true) continue
            // Space already committed before this child: what its predecessors took, plus the i gaps
            // that sit in front of it. Weighted siblings count as zero -- they take the leftovers.
            val placeable = measurables[i].measure(looseMainAxis(constraints, used = total + gap * i))
            placeables[i] = placeable
            total += orientation.mainAxisSize(placeable)
        }
        return total
    }

    /**
     * Measures every weighted child against its share of the leftover space, returning their total.
     *
     * Weight under an unbounded main axis is a no-op, matching Compose: there is no leftover space
     * to take a share of, so the child keeps its own content size rather than collapsing to a zero
     * share. `ui-core`'s FillMaxUnboundedParentTest guards the same rule from the fillMax side.
     */
    private fun measureWeighted(
        measurables: List<Measurable>,
        placeables: Array<Placeable?>,
        constraints: Constraints,
        occupied: Int,
        totalWeight: Float,
    ): Int {
        val shares = if (orientation.hasBoundedMainAxis(constraints)) {
            weightedShares(measurables, constraints, occupied, totalWeight)
        } else {
            null
        }
        var total = 0
        for (i in measurables.indices) {
            val data = measurables[i].rowColumnParentData()
            if (data == null || data.weight <= 0f) continue
            val childConstraints = if (shares == null) {
                looseMainAxis(constraints)
            } else {
                orientation.constraintsFor(
                    mainMin = if (data.fill) shares[i] else 0,
                    mainMax = shares[i],
                    crossMin = 0,
                    crossMax = orientation.crossAxisMax(constraints),
                )
            }
            val placeable = measurables[i].measure(childConstraints)
            placeables[i] = placeable
            total += orientation.mainAxisSize(placeable)
        }
        return total
    }

    /**
     * Each weighted child's main-axis size in pixels.
     *
     * Rounding each share independently loses up to `n - 1` px of the space the container was told
     * to fill, which reads as a gap after the last child. The leftover is spread one pixel at a time
     * instead of dropped -- what Compose does, and the cost of Int layout that
     * `docs/reference/compose-engine/01-layout.md` names explicitly.
     */
    private fun weightedShares(
        measurables: List<Measurable>,
        constraints: Constraints,
        occupied: Int,
        totalWeight: Float,
    ): IntArray {
        val shares = IntArray(measurables.size)
        val slack = (orientation.mainAxisMax(constraints) - occupied).coerceAtLeast(0)
        val perWeight = slack.toFloat() / totalWeight
        var remainder = slack
        for (i in shares.indices) {
            val weight = measurables[i].rowColumnParentData()?.weight ?: 0f
            if (weight <= 0f) continue
            val share = (perWeight * weight).roundToInt()
            shares[i] = share
            remainder -= share
        }

        val step = if (remainder > 0) 1 else -1
        var left = abs(remainder)
        var index = 0
        while (left > 0 && index < shares.size) {
            val weighted = (measurables[index].rowColumnParentData()?.weight ?: 0f) > 0f
            if (weighted && shares[index] + step >= 0) {
                shares[index] += step
                left--
            }
            index++
        }
        return shares
    }

    /**
     * Lets a child report its own main-axis size within what is left, cross axis as offered.
     *
     * The main axis keeps its bound. Handing down `Infinity` instead loses the parent's size one
     * level at a time, and the loss lands on the *cross* axis of the next transposed container: a
     * Row inside a bounded Column saw an unbounded height, so `fillMaxHeight()` on a Row child
     * became a no-op and a childless one -- shadcn's `w-px` resizable divider -- measured 0 and
     * painted nothing.
     */
    private fun looseMainAxis(constraints: Constraints, used: Int = 0): Constraints {
        val mainMax = orientation.mainAxisMax(constraints)
        return orientation.constraintsFor(
            mainMin = 0,
            mainMax = if (mainMax == Constraints.Infinity) mainMax else (mainMax - used).coerceAtLeast(0),
            crossMin = 0,
            crossMax = orientation.crossAxisMax(constraints),
        )
    }
}

/** Arrangement spacing sits between children, so n children carry n-1 gaps. */
private inline fun Density.sumWithGaps(
    measurables: List<IntrinsicMeasurable>,
    spacing: Dp,
    size: (IntrinsicMeasurable) -> Int,
): Int {
    var total = 0
    for (i in measurables.indices) total += size(measurables[i])
    return total + spacing.roundToPx() * (measurables.size - 1).coerceAtLeast(0)
}

private fun totalWeightOf(measurables: List<Measurable>): Float {
    var total = 0f
    for (i in measurables.indices) {
        total += measurables[i].rowColumnParentData()?.weight ?: 0f
    }
    return total
}
