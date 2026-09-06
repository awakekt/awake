/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.layout

import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.VerticalAlignmentLine

/**
 * What a Column's direct children may say about themselves.
 */
@LayoutScopeMarker
interface ColumnScope {
    /** Claims a share of the vertical space this Column's other children did not take. */
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier

    /** Aligns this child horizontally within the Column. */
    fun Modifier.align(alignment: Alignment.Horizontal): Modifier

    /** Aligns this child by the specified vertical alignment line within the Column. */
    fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier
}

internal object ColumnScopeInstance : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
        require(weight > 0f) { "weight must be > 0, was $weight" }
        return this then LayoutWeightElement(weight, fill)
    }

    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier =
        this then HorizontalAlignElement(alignment)

    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier =
        this then AlignmentLineElement(alignmentLine)
}

/** Stacks children vertically. See [RowColumnMeasurePolicy] for the shared implementation. */
class ColumnMeasurePolicy(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) : MeasurePolicy by RowColumnMeasurePolicy(
    LayoutOrientation.Vertical,
    verticalArrangement.spacing,
    horizontalAlignment::align,
    verticalArrangement::leading,
    verticalArrangement::between,
)
