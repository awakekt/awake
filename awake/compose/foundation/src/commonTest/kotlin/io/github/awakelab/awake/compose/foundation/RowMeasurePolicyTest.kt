/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Arrangement
import io.github.awakelab.awake.compose.foundation.layout.RowMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.RowScopeInstance
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

private fun rowWeight(value: Float, fill: Boolean = true): Modifier =
    with(RowScopeInstance) { Modifier.weight(value, fill) }

private fun row(
    vararg children: LayoutNode,
    arrangement: Arrangement.Horizontal = Arrangement.Start,
    alignment: Alignment.Vertical = Alignment.Top,
): LayoutNode = LayoutNode(RowMeasurePolicy(arrangement, alignment))
    .also { children.forEach(it.children::add) }

/**
 * Row is the transpose of Column and shares its implementation, so these are the same cases run on
 * the other axis. A divergence here means the shared policy leaked an axis assumption.
 */
class RowMeasurePolicyTest {

    @Test
    fun childrenLineUpAndTheRowHugsThem() {
        val a = child(10, 30)
        val b = child(20, 50)
        val rowNode = row(a, b).layoutIn(Constraints.of(0, Constraints.Infinity, 0, 200))

        assertEquals(30, rowNode.width, "row hugs the summed widths")
        assertEquals(50, rowNode.height, "row hugs its tallest child")
        assertEquals(0, a.absoluteX)
        assertEquals(10, b.absoluteX)
    }

    @Test
    fun arrangementSpacingSitsBetweenChildrenOnly() {
        val a = child(10, 10)
        val b = child(10, 10)
        val c = child(10, 10)
        val rowNode = row(a, b, c, arrangement = Arrangement.spacedByHorizontal(4.dp))
            .layoutIn(Constraints.of(0, Constraints.Infinity, 0, 200))

        assertEquals(38, rowNode.width)
        assertEquals(0, a.absoluteX)
        assertEquals(14, b.absoluteX)
        assertEquals(28, c.absoluteX)
    }

    @Test
    fun aWeightedChildTakesWhatTheOthersLeft() {
        val leading = child(20, 10)
        val spacer = child(5, 10, rowWeight(1f))
        val trailing = child(30, 10)
        row(leading, spacer, trailing).layoutIn(Constraints.fixed(200, 100))

        assertEquals(150, spacer.width, "200 - 20 - 30")
        assertEquals(20, spacer.absoluteX)
        assertEquals(170, trailing.absoluteX)
    }

    @Test
    fun theRoundingRemainderIsSpreadRatherThanDropped() {
        val a = child(0, 10, rowWeight(1f))
        val b = child(0, 10, rowWeight(1f))
        val c = child(0, 10, rowWeight(1f))
        val rowNode = row(a, b, c).layoutIn(Constraints.fixed(100, 10))

        assertEquals(listOf(34, 33, 33), listOf(a.width, b.width, c.width))
        assertEquals(100, rowNode.width)
    }

    @Test
    fun weightIsANoOpUnderAnUnboundedWidth() {
        val a = child(25, 10, rowWeight(1f))
        val rowNode = row(a).layoutIn(Constraints.of(0, Constraints.Infinity, 0, 100))

        assertEquals(25, a.width)
        assertEquals(25, rowNode.width)
    }

    @Test
    fun anUnweightedChildIsClampedToWhatItsPredecessorsLeft() {
        val first = child(150, 10)
        val second = child(100, 10)
        row(first, second).layoutIn(Constraints.fixed(200, 100))

        assertEquals(150, first.width)
        assertEquals(50, second.width, "200 - 150, rather than overflowing the row")
    }

    @Test
    fun crossAxisAlignmentCentersShortChildren() {
        val short = child(10, 20)
        val tall = child(10, 80)
        row(short, tall, alignment = Alignment.CenterVertically)
            .layoutIn(Constraints.of(0, Constraints.Infinity, 0, 80))

        assertEquals(30, short.absoluteY, "(80 - 20) / 2")
        assertEquals(0, tall.absoluteY)
    }

    @Test
    fun crossAxisBottomAlignmentPushesChildrenDown() {
        val short = child(10, 20)
        val tall = child(10, 80)
        row(short, tall, alignment = Alignment.Bottom)
            .layoutIn(Constraints.of(0, Constraints.Infinity, 0, 80))

        assertEquals(60, short.absoluteY, "80 - 20")
        assertEquals(0, tall.absoluteY)
    }

    @Test
    fun everyChildIsMeasuredExactlyOnce() {
        val plain = FixedSize(10, 10)
        val weighted = FixedSize(10, 10)
        val rowNode = LayoutNode(RowMeasurePolicy()).also {
            it.children.add(LayoutNode(plain))
            it.children.add(LayoutNode(weighted).also { node -> node.modifier = rowWeight(1f) })
        }
        rowNode.layoutIn(Constraints.fixed(200, 100))

        assertEquals(1, plain.measureCount)
        assertEquals(1, weighted.measureCount)
    }

    @Test
    fun childrenWithBaselineAlignAlongTheSameLine() {
        // Child A: height 40, baseline at y=30 (10px below baseline)
        // Child B: height 60, baseline at y=45 (15px below baseline)
        val a = childWithBaseline(20, 40, 30, with(RowScopeInstance) { Modifier.alignByBaseline() })
        val b = childWithBaseline(30, 60, 45, with(RowScopeInstance) { Modifier.alignByBaseline() })
        val rowNode = row(a, b).layoutIn(Constraints.of(0, Constraints.Infinity, 0, 200))

        // Max baseline is 45 (from B).
        // A must be placed at y = 45 - 30 = 15 so its baseline is at 15 + 30 = 45.
        // B must be placed at y = 45 - 45 = 0 so its baseline is at 0 + 45 = 45.
        // Total row height is maxBaseline (45) + maxBelow (15) = 60.
        assertEquals(15, a.absoluteY, "A is placed at y=15 so baseline is at y=45")
        assertEquals(0, b.absoluteY, "B is placed at y=0 so baseline is at y=45")
        assertEquals(60, rowNode.height)
    }

    @Test
    fun perChildVerticalAlignmentOverridesRowAlignment() {
        val top = child(10, 20, with(RowScopeInstance) { Modifier.align(Alignment.Top) })
        val bottom = child(10, 20, with(RowScopeInstance) { Modifier.align(Alignment.Bottom) })
        val center = child(10, 20)
        val tall = child(10, 80)
        row(top, bottom, center, tall, alignment = Alignment.CenterVertically)
            .layoutIn(Constraints.of(0, Constraints.Infinity, 0, 80))

        assertEquals(0, top.absoluteY, "per-child Top overrides row Center")
        assertEquals(60, bottom.absoluteY, "per-child Bottom overrides row Center")
        assertEquals(30, center.absoluteY, "unaligned child follows row Center")
        assertEquals(0, tall.absoluteY, "tall child matches row height")
    }
}
