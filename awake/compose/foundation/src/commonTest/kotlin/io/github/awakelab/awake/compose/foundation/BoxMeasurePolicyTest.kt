/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.BoxMeasurePolicy
import io.github.awakelab.awake.compose.ui.Alignment
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertEquals

private fun box(
    vararg children: LayoutNode,
    horizontal: Alignment.Horizontal = Alignment.Start,
    vertical: Alignment.Vertical = Alignment.Top,
): LayoutNode = LayoutNode(BoxMeasurePolicy(horizontal, vertical))
    .also { children.forEach(it.children::add) }

class BoxMeasurePolicyTest {

    @Test
    fun theBoxShrinkWrapsToItsLargestChild() {
        // Compose's default. ui-core's box() defaults to FillMax, which mirror-map records as a
        // divergence and which this engine deliberately does not carry forward.
        val small = child(20, 10)
        val large = child(50, 30)
        val boxNode = box(small, large)
            .layoutIn(Constraints.of(0, 500, 0, 500))

        assertEquals(50, boxNode.width)
        assertEquals(30, boxNode.height)
    }

    @Test
    fun childrenStackAtTheSameOrigin() {
        val a = child(20, 10)
        val b = child(30, 20)
        box(a, b).layoutIn(Constraints.of(0, 500, 0, 500))

        assertEquals(0, a.absoluteX)
        assertEquals(0, a.absoluteY)
        assertEquals(0, b.absoluteX)
        assertEquals(0, b.absoluteY)
    }

    @Test
    fun alignmentPositionsEachChildIndependently() {
        val small = child(20, 10)
        val large = child(60, 40)
        box(small, large, horizontal = Alignment.CenterHorizontally, vertical = Alignment.Bottom)
            .layoutIn(Constraints.of(0, 500, 0, 500))

        assertEquals(20, small.absoluteX, "(60 - 20) / 2")
        assertEquals(30, small.absoluteY, "40 - 10")
        assertEquals(0, large.absoluteX, "the largest child fills, so alignment is a no-op for it")
    }

    @Test
    fun endAlignmentPushesToTheTrailingEdge() {
        val small = child(20, 10)
        val large = child(60, 10)
        box(small, large, horizontal = Alignment.End).layoutIn(Constraints.of(0, 500, 0, 500))

        assertEquals(40, small.absoluteX, "60 - 20")
    }

    @Test
    fun childrenNeverInheritTheBoxMinimum() {
        // A minimum belongs to the box, not to what it holds -- otherwise a Box with a floor would
        // silently stretch every child to match it.
        val small = child(20, 10)
        val boxNode = box(small).layoutIn(Constraints.of(100, 500, 100, 500))

        assertEquals(20, small.width)
        assertEquals(10, small.height)
        assertEquals(100, boxNode.width, "the box itself still honours its minimum")
        assertEquals(100, boxNode.height)
    }

    @Test
    fun anEmptyBoxCollapsesToItsMinimum() {
        val boxNode = box().layoutIn(Constraints.of(0, 500, 0, 500))

        assertEquals(0, boxNode.width)
        assertEquals(0, boxNode.height)
    }

    @Test
    fun everyChildIsMeasuredExactlyOnce() {
        val a = FixedSize(10, 10)
        val b = FixedSize(20, 20)
        LayoutNode(BoxMeasurePolicy()).also {
            it.children.add(LayoutNode(a))
            it.children.add(LayoutNode(b))
        }.layoutIn(Constraints.of(0, 500, 0, 500))

        assertEquals(1, a.measureCount)
        assertEquals(1, b.measureCount)
    }
}
