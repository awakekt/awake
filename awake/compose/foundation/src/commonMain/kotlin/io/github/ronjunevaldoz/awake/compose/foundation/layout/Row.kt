// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

import io.github.ronjunevaldoz.awake.compose.ui.Alignment
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.node.ParentDataModifierNode

/** What a Row's direct children may say about themselves. See [ColumnScope]. */
@LayoutScopeMarker
interface RowScope {
    /** Claims a share of the horizontal space this Row's other children did not take. */
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

internal object RowScopeInstance : RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
        require(weight > 0f) { "weight must be > 0, was $weight" }
        return this then LayoutWeightNode(LayoutWeight(weight, fill))
    }
}

/** Stacks children horizontally. See [RowColumnMeasurePolicy] for the shared implementation. */
class RowMeasurePolicy(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
) : MeasurePolicy by RowColumnMeasurePolicy(
    LayoutOrientation.Horizontal,
    horizontalArrangement.spacing,
    verticalAlignment::align,
)

internal class LayoutWeightNode(private val data: LayoutWeight) : ParentDataModifierNode {
    override fun modifyParentData(current: Any?): Any? = data

    override fun toString(): String = "weight(${data.weight}, fill=${data.fill})"
}
