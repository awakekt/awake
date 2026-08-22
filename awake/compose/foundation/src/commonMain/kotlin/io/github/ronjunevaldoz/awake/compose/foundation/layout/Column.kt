// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.layout

import io.github.ronjunevaldoz.awake.compose.ui.Alignment
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy

/**
 * What a Column's direct children may say about themselves.
 *
 * `weight()` lives here rather than on `Modifier` so that using it outside a Column is a compile
 * error instead of a silently ignored modifier -- the failure `ui-core` throws at runtime to catch.
 * [LayoutScopeMarker] additionally stops an enclosing Row's members from leaking in.
 */
@LayoutScopeMarker
interface ColumnScope {
    /** Claims a share of the vertical space this Column's other children did not take. */
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

internal object ColumnScopeInstance : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
        require(weight > 0f) { "weight must be > 0, was $weight" }
        return this then LayoutWeightNode(LayoutWeight(weight, fill))
    }
}

/** Stacks children vertically. See [RowColumnMeasurePolicy] for the shared implementation. */
class ColumnMeasurePolicy(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) : MeasurePolicy by RowColumnMeasurePolicy(
    LayoutOrientation.Vertical,
    verticalArrangement.spacing,
    horizontalAlignment::align,
)
