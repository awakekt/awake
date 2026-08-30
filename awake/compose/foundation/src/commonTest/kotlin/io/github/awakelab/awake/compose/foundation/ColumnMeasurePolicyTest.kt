/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.ColumnScopeInstance
import io.github.awakelab.awake.compose.foundation.layout.RowMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.fillMaxHeight
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

private fun columnWeight(value: Float, fill: Boolean = true): Modifier =
    with(ColumnScopeInstance) { Modifier.weight(value, fill) }

private fun column(
    vararg children: LayoutNode,
    arrangement: Arrangement.Vertical = Arrangement.Top,
    alignment: Alignment.Horizontal = Alignment.Start,
): LayoutNode = LayoutNode(ColumnMeasurePolicy(arrangement, alignment))
    .also { children.forEach(it.children::add) }

class ColumnMeasurePolicyTest {

    @Test
    fun childrenStackAndTheColumnHugsThem() {
        val a = child(30, 10)
        val b = child(50, 20)
        val col = column(a, b).layoutIn(Constraints.of(0, 200, 0, Constraints.Infinity))

        assertEquals(50, col.width, "column hugs its widest child")
        assertEquals(30, col.height, "column hugs the stacked heights")
        assertEquals(0, a.absoluteY)
        assertEquals(10, b.absoluteY)
    }

    @Test
    fun arrangementSpacingSitsBetweenChildrenOnly() {
        val a = child(10, 10)
        val b = child(10, 10)
        val c = child(10, 10)
        val col = column(a, b, c, arrangement = Arrangement.spacedBy(4.dp))
            .layoutIn(Constraints.of(0, 200, 0, Constraints.Infinity))

        // Three children, two gaps -- not three.
        assertEquals(38, col.height)
        assertEquals(0, a.absoluteY)
        assertEquals(14, b.absoluteY)
        assertEquals(28, c.absoluteY)
    }

    @Test
    fun aWeightedChildTakesWhatTheOthersLeft() {
        val header = child(10, 20)
        val body = child(10, 5, columnWeight(1f))
        val footer = child(10, 30)
        column(header, body, footer).layoutIn(Constraints.fixed(100, 200))

        assertEquals(150, body.height, "200 - 20 - 30")
        assertEquals(20, body.absoluteY)
        assertEquals(170, footer.absoluteY)
    }

    @Test
    fun twoWeightedChildrenSplitProportionally() {
        val a = child(10, 0, columnWeight(1f))
        val b = child(10, 0, columnWeight(3f))
        column(a, b).layoutIn(Constraints.fixed(100, 200))

        assertEquals(50, a.height)
        assertEquals(150, b.height)
    }

    @Test
    fun theRoundingRemainderIsSpreadRatherThanDropped() {
        // 100 / 3 rounds to 33 each, which would leave the column 1px short of the height it was
        // told to fill -- visible as a gap under the last child. See 01-layout.md's "what Int costs".
        val a = child(10, 0, columnWeight(1f))
        val b = child(10, 0, columnWeight(1f))
        val c = child(10, 0, columnWeight(1f))
        val col = column(a, b, c).layoutIn(Constraints.fixed(10, 100))

        assertEquals(100, a.height + b.height + c.height, "the whole height is distributed")
        assertEquals(listOf(34, 33, 33), listOf(a.height, b.height, c.height))
        assertEquals(100, col.height)
    }

    @Test
    fun weightIsANoOpUnderAnUnboundedHeight() {
        // No slack exists, so the child keeps its content height instead of collapsing to zero.
        val a = child(10, 25, columnWeight(1f))
        val col = column(a).layoutIn(Constraints.of(0, 100, 0, Constraints.Infinity))

        assertEquals(25, a.height)
        assertEquals(25, col.height)
    }

    @Test
    fun nonFillingWeightCapsTheShareWithoutForcingIt() {
        val capped = child(10, 12, columnWeight(1f, fill = false))
        column(capped).layoutIn(Constraints.fixed(100, 200))

        assertEquals(12, capped.height, "fill = false caps at the share but keeps content height")
    }

    @Test
    fun crossAxisAlignmentCentersNarrowChildren() {
        val narrow = child(20, 10)
        val wide = child(80, 10)
        column(narrow, wide, alignment = Alignment.CenterHorizontally)
            .layoutIn(Constraints.of(0, 80, 0, Constraints.Infinity))

        assertEquals(30, narrow.absoluteX, "(80 - 20) / 2")
        assertEquals(0, wide.absoluteX)
    }

    @Test
    fun everyChildIsMeasuredExactlyOnce() {
        // The contract the engine exists for. ui-core re-runs a content lambda to learn a size,
        // which is what multiplies with nesting -- 7,696 trial passes on one showcase page.
        val plain = FixedSize(10, 10)
        val weighted = FixedSize(10, 10)
        val col = LayoutNode(ColumnMeasurePolicy()).also {
            it.children.add(LayoutNode(plain))
            it.children.add(LayoutNode(weighted).also { node -> node.modifier = columnWeight(1f) })
        }
        col.layoutIn(Constraints.fixed(100, 200))

        assertEquals(1, plain.measureCount)
        assertEquals(1, weighted.measureCount)
    }

    @Test
    fun nestingDoesNotMultiplyMeasures() {
        val leaf = FixedSize(10, 10)
        var node = LayoutNode(leaf)
        repeat(8) {
            node = LayoutNode(ColumnMeasurePolicy()).also { parent -> parent.children.add(node) }
        }
        node.layoutIn(Constraints.of(0, 500, 0, Constraints.Infinity))

        // Depth 8. ui-core's TrialMeasureScalingTest measured 2^depth..3^depth here.
        assertEquals(1, leaf.measureCount)
    }

    @Test
    fun aBoundedColumnOffersItsHeightThroughARowToAFillingChild() {
        // shadcn's `w-px` resizable divider has no content of its own, so fillMaxHeight() is the
        // only thing that can give it a height. A column that handed Infinity down turned that into
        // a no-op two levels later, and the divider measured 0 and painted nothing.
        val divider = child(1, 0, Modifier.fillMaxHeight())
        val label = child(40, 24)
        val row = LayoutNode(RowMeasurePolicy()).also {
            it.children.add(divider)
            it.children.add(label)
        }
        val col = column(row).layoutIn(Constraints.of(0, 360, 0, 160))

        assertEquals(160, divider.height, "the column's bound survives the row's cross axis")
        assertEquals(160, col.height)
    }

    @Test
    fun perChildHorizontalAlignmentOverridesColumnAlignment() {
        val start = child(20, 10, with(ColumnScopeInstance) { Modifier.align(Alignment.Start) })
        val end = child(20, 10, with(ColumnScopeInstance) { Modifier.align(Alignment.End) })
        val wide = child(80, 10)
        column(start, end, wide, alignment = Alignment.CenterHorizontally)
            .layoutIn(Constraints.of(0, 80, 0, Constraints.Infinity))

        assertEquals(0, start.absoluteX, "per-child Start overrides column Center")
        assertEquals(60, end.absoluteX, "per-child End overrides column Center")
        assertEquals(0, wide.absoluteX)
    }
}
