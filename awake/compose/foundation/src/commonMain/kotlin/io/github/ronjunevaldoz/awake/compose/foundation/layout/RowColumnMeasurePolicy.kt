// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

import io.github.ronjunevaldoz.awake.compose.ui.layout.IntrinsicMeasurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.layout.Placeable
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.Density
import io.github.ronjunevaldoz.awake.compose.ui.unit.Dp
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

/** A child's share of the leftover main-axis space. [fill] tightens the share; otherwise it caps it. */
internal class LayoutWeight(val weight: Float, val fill: Boolean)

/** Where a child sits on the cross axis: `(childSize, containerSize) -> offset`. */
internal typealias CrossAxisAlign = (Int, Int) -> Int

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

        val unweightedMain = measureUnweighted(measurables, placeables, constraints)
        val totalWeight = totalWeightOf(measurables)
        val weightedMain = if (totalWeight > 0f) {
            measureWeighted(measurables, placeables, constraints, unweightedMain + totalGap, totalWeight)
        } else {
            0
        }

        val main = unweightedMain + weightedMain + totalGap
        val cross = maxCrossOf(placeables)
        val width = constraints.constrainWidth(if (isVertical) cross else main)
        val height = constraints.constrainHeight(if (isVertical) main else cross)
        val crossSpace = if (isVertical) width else height

        return layout(width, height) {
            var cursor = 0
            for (i in placeables.indices) {
                val placeable = placeables[i] ?: continue
                val crossOffset = crossAxisAlign(orientation.crossAxisSize(placeable), crossSpace)
                if (isVertical) {
                    placeable.placeAt(crossOffset, cursor)
                } else {
                    placeable.placeAt(cursor, crossOffset)
                }
                cursor += orientation.mainAxisSize(placeable) + gap
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
    ): Int {
        var total = 0
        for (i in measurables.indices) {
            if (measurables[i].layoutWeight() != null) continue
            val placeable = measurables[i].measure(looseMainAxis(constraints))
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
            val weight = measurables[i].layoutWeight() ?: continue
            val childConstraints = if (shares == null) {
                looseMainAxis(constraints)
            } else {
                orientation.constraintsFor(
                    mainMin = if (weight.fill) shares[i] else 0,
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
            val weight = measurables[i].layoutWeight() ?: continue
            val share = (perWeight * weight.weight).roundToInt()
            shares[i] = share
            remainder -= share
        }

        val step = if (remainder > 0) 1 else -1
        var left = abs(remainder)
        var index = 0
        while (left > 0 && index < shares.size) {
            val weighted = measurables[index].layoutWeight() != null
            if (weighted && shares[index] + step >= 0) {
                shares[index] += step
                left--
            }
            index++
        }
        return shares
    }

    private fun maxCrossOf(placeables: Array<Placeable?>): Int {
        var max = 0
        for (i in placeables.indices) {
            val placeable = placeables[i] ?: continue
            max = maxOf(max, orientation.crossAxisSize(placeable))
        }
        return max
    }

    /** Lets a child report its own main-axis size, while the cross axis stays as offered. */
    private fun looseMainAxis(constraints: Constraints): Constraints =
        orientation.constraintsFor(0, Constraints.Infinity, 0, orientation.crossAxisMax(constraints))
}

internal fun Measurable.layoutWeight(): LayoutWeight? = parentData as? LayoutWeight

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
        total += measurables[i].layoutWeight()?.weight ?: 0f
    }
    return total
}
