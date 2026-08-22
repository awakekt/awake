// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.layout.Arrangement
import io.github.ronjunevaldoz.awake.compose.foundation.layout.RowMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.RowScopeInstance
import io.github.ronjunevaldoz.awake.compose.ui.Alignment
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
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
}
