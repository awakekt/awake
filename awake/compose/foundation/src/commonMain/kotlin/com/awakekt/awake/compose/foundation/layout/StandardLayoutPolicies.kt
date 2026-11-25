/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.Slot
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.platform.LocalFontScale
import com.awakekt.awake.compose.ui.unit.LocalLayoutDirection

private object BoxNodeType

internal fun sameLayoutValue(a: Any, b: Any): Boolean = a === b || a == b

context(composer: Composer)
internal fun configureLayoutNode(node: LayoutNode, modifier: Modifier, policy: MeasurePolicy) {
    node.measurePolicy = policy
    node.modifier = modifier
    node.density = LocalDensity.current
    node.fontScale = LocalFontScale.current
    node.layoutDirection = LocalLayoutDirection.current
}

@PublishedApi
internal fun boxScopeInstance(): BoxScope = BoxScopeInstance

context(composer: Composer)
internal fun emitEmptyBoxNode(modifier: Modifier) {
    val node = composer.startNode(BoxNodeType, Slot.Children) as LayoutNode
    configureLayoutNode(node, modifier, StandardLayoutPolicies.topStartBox)
    composer.endEmptyNode()
}

@PublishedApi
context(composer: Composer)
internal fun openBoxNode(modifier: Modifier, contentAlignment: Alignment): Any? {
    val node = composer.startNode(BoxNodeType, Slot.Children) as LayoutNode
    val previous = node.measurePolicy as? BoxMeasurePolicy
    val policy = if (previous != null && sameLayoutValue(previous.alignment, contentAlignment)) {
        previous
    } else {
        StandardLayoutPolicies.box(contentAlignment) ?: BoxMeasurePolicy(contentAlignment)
    }
    configureLayoutNode(node, modifier, policy)
    return composer.enterNode()
}

@PublishedApi
context(composer: Composer)
internal fun openAxisBoxNode(
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal,
    verticalAlignment: Alignment.Vertical,
): Any? {
    val standard = StandardLayoutPolicies.axisAlignment(horizontalAlignment, verticalAlignment)
    if (standard != null) return openBoxNode(modifier, standard)
    val node = composer.startNode(BoxNodeType, Slot.Children) as LayoutNode
    val previous = node.measurePolicy as? BoxMeasurePolicy
    val previousAxis = previous?.alignment as? AxisAlignment
    val policy = if (previousAxis != null &&
        sameLayoutValue(previousAxis.horizontal, horizontalAlignment) &&
        sameLayoutValue(previousAxis.vertical, verticalAlignment)
    ) {
        previous
    } else {
        BoxMeasurePolicy(horizontalAlignment, verticalAlignment)
    }
    configureLayoutNode(node, modifier, policy)
    return composer.enterNode()
}

/**
 * Pre-allocated immutable measure policies for standard [Alignment] and [Arrangement] singletons,
 * shared across all `Box`, `Row`, and `Column` nodes so standard containers allocate nothing even
 * on their first composition pass.
 */
internal object StandardLayoutPolicies {
    val topStartBox: BoxMeasurePolicy = BoxMeasurePolicy(Alignment.TopStart)

    private val boxPolicies: Array<BoxMeasurePolicy> = arrayOf(
        topStartBox,
        BoxMeasurePolicy(Alignment.TopCenter),
        BoxMeasurePolicy(Alignment.TopEnd),
        BoxMeasurePolicy(Alignment.CenterStart),
        BoxMeasurePolicy(Alignment.Center),
        BoxMeasurePolicy(Alignment.CenterEnd),
        BoxMeasurePolicy(Alignment.BottomStart),
        BoxMeasurePolicy(Alignment.BottomCenter),
        BoxMeasurePolicy(Alignment.BottomEnd),
    )

    private val axisAlignments: Array<Alignment> = arrayOf(
        Alignment.TopStart,
        Alignment.TopCenter,
        Alignment.TopEnd,
        Alignment.CenterStart,
        Alignment.Center,
        Alignment.CenterEnd,
        Alignment.BottomStart,
        Alignment.BottomCenter,
        Alignment.BottomEnd,
    )

    private val rowPolicies: Array<RowMeasurePolicy> = arrayOf(
        RowMeasurePolicy(Arrangement.Start, Alignment.Top),
        RowMeasurePolicy(Arrangement.Start, Alignment.CenterVertically),
        RowMeasurePolicy(Arrangement.Start, Alignment.Bottom),
        RowMeasurePolicy(Arrangement.End, Alignment.Top),
        RowMeasurePolicy(Arrangement.End, Alignment.CenterVertically),
        RowMeasurePolicy(Arrangement.End, Alignment.Bottom),
        RowMeasurePolicy(Arrangement.CenterHorizontally, Alignment.Top),
        RowMeasurePolicy(Arrangement.CenterHorizontally, Alignment.CenterVertically),
        RowMeasurePolicy(Arrangement.CenterHorizontally, Alignment.Bottom),
        RowMeasurePolicy(Arrangement.SpaceBetweenHorizontal, Alignment.Top),
        RowMeasurePolicy(Arrangement.SpaceBetweenHorizontal, Alignment.CenterVertically),
        RowMeasurePolicy(Arrangement.SpaceBetweenHorizontal, Alignment.Bottom),
    )

    private val columnPolicies: Array<ColumnMeasurePolicy> = arrayOf(
        ColumnMeasurePolicy(Arrangement.Top, Alignment.Start),
        ColumnMeasurePolicy(Arrangement.Top, Alignment.CenterHorizontally),
        ColumnMeasurePolicy(Arrangement.Top, Alignment.End),
        ColumnMeasurePolicy(Arrangement.Bottom, Alignment.Start),
        ColumnMeasurePolicy(Arrangement.Bottom, Alignment.CenterHorizontally),
        ColumnMeasurePolicy(Arrangement.Bottom, Alignment.End),
        ColumnMeasurePolicy(Arrangement.Center, Alignment.Start),
        ColumnMeasurePolicy(Arrangement.Center, Alignment.CenterHorizontally),
        ColumnMeasurePolicy(Arrangement.Center, Alignment.End),
        ColumnMeasurePolicy(Arrangement.SpaceBetween, Alignment.Start),
        ColumnMeasurePolicy(Arrangement.SpaceBetween, Alignment.CenterHorizontally),
        ColumnMeasurePolicy(Arrangement.SpaceBetween, Alignment.End),
    )

    fun box(alignment: Alignment): BoxMeasurePolicy? = when {
        alignment === Alignment.TopStart -> boxPolicies[0]
        alignment === Alignment.TopCenter -> boxPolicies[1]
        alignment === Alignment.TopEnd -> boxPolicies[2]
        alignment === Alignment.CenterStart -> boxPolicies[3]
        alignment === Alignment.Center -> boxPolicies[4]
        alignment === Alignment.CenterEnd -> boxPolicies[5]
        alignment === Alignment.BottomStart -> boxPolicies[6]
        alignment === Alignment.BottomCenter -> boxPolicies[7]
        alignment === Alignment.BottomEnd -> boxPolicies[8]
        else -> null
    }

    fun axisAlignment(horizontal: Alignment.Horizontal, vertical: Alignment.Vertical): Alignment? {
        val v = verticalIndex(vertical)
        val h = horizontalIndex(horizontal)
        return if (v >= 0 && h >= 0) axisAlignments[v * 3 + h] else null
    }

    fun row(arrangement: Arrangement.Horizontal, alignment: Alignment.Vertical): RowMeasurePolicy? {
        val a = when {
            arrangement === Arrangement.Start -> 0
            arrangement === Arrangement.End -> 1
            arrangement === Arrangement.CenterHorizontally -> 2
            arrangement === Arrangement.SpaceBetweenHorizontal -> 3
            else -> -1
        }
        val v = verticalIndex(alignment)
        return if (a >= 0 && v >= 0) rowPolicies[a * 3 + v] else null
    }

    fun column(arrangement: Arrangement.Vertical, alignment: Alignment.Horizontal): ColumnMeasurePolicy? {
        val a = when {
            arrangement === Arrangement.Top -> 0
            arrangement === Arrangement.Bottom -> 1
            arrangement === Arrangement.Center -> 2
            arrangement === Arrangement.SpaceBetween -> 3
            else -> -1
        }
        val h = horizontalIndex(alignment)
        return if (a >= 0 && h >= 0) columnPolicies[a * 3 + h] else null
    }

    private fun verticalIndex(alignment: Alignment.Vertical): Int = when {
        alignment === Alignment.Top -> 0
        alignment === Alignment.CenterVertically -> 1
        alignment === Alignment.Bottom -> 2
        else -> -1
    }

    private fun horizontalIndex(alignment: Alignment.Horizontal): Int = when {
        alignment === Alignment.Start -> 0
        alignment === Alignment.CenterHorizontally -> 1
        alignment === Alignment.End -> 2
        else -> -1
    }
}
