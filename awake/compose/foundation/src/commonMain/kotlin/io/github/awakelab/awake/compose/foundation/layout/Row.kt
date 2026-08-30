/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.layout

import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.layout.FirstBaseline
import io.github.awakelab.awake.compose.ui.layout.HorizontalAlignmentLine
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy

/** What a Row's direct children may say about themselves. See [ColumnScope]. */
@LayoutScopeMarker
interface RowScope {
    /** Claims a share of the horizontal space this Row's other children did not take. */
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier

    /** Aligns this child vertically within the Row. */
    fun Modifier.align(alignment: Alignment.Vertical): Modifier

    /** Aligns this child by the specified horizontal alignment line within the Row. */
    fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier

    /** Aligns this child's first text baseline with sibling baseline-aligned children. */
    fun Modifier.alignByBaseline(): Modifier = alignBy(FirstBaseline)
}

internal object RowScopeInstance : RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
        require(weight > 0f) { "weight must be > 0, was $weight" }
        return this then LayoutWeightElement(weight, fill)
    }

    override fun Modifier.align(alignment: Alignment.Vertical): Modifier =
        this then VerticalAlignElement(alignment)

    override fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier =
        this then AlignmentLineElement(alignmentLine)
}

/** Stacks children horizontally. See [RowColumnMeasurePolicy] for the shared implementation. */
class RowMeasurePolicy(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
) : MeasurePolicy by RowColumnMeasurePolicy(
    LayoutOrientation.Horizontal,
    horizontalArrangement.spacing,
    verticalAlignment::align,
    horizontalArrangement::leading,
    horizontalArrangement::between,
)
