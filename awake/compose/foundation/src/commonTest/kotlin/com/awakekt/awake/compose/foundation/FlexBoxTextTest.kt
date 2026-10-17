/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.FlexAlignContent
import com.awakekt.awake.compose.foundation.layout.FlexAlignItems
import com.awakekt.awake.compose.foundation.layout.FlexBox
import com.awakekt.awake.compose.foundation.layout.FlexBoxConfigScope
import com.awakekt.awake.compose.foundation.layout.FlexBoxScope
import com.awakekt.awake.compose.foundation.layout.FlexDirection
import com.awakekt.awake.compose.foundation.layout.FlexWrap
import com.awakekt.awake.compose.foundation.layout.IntrinsicSize
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A wrapping [FlexBox] of labels and fixed-size items, at several widths and font scales.
 *
 * Every size here comes from measuring, never from a number written down, so the same checks hold
 * whatever font the host ships: items never overlap, every item sits inside the box, a line is as
 * tall as its tallest item, and the box is exactly as tall as its lines and gaps.
 */
class FlexBoxTextTest {
    private val widths = listOf(120, 160, 240, 400)
    private val fontScales = listOf(1f, 1.5f, 2f)
    private val gap = 4

    @Test
    fun wrappedLinesHoldTheirMeasuredTextWithoutOverlap() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.width(width.dp).testTag("flex"), config = { wrapWithGap() }) { mixed() }
        }

        val flex = nodes.getValue("flex")
        val items = itemTags.map(nodes::getValue)
        assertNoOverlap(items, "width $width, scale $fontScale")
        items.forEach { assertInside(flex, it, "width $width, scale $fontScale") }
        val lines = linesOf(items)
        val expectedHeight = lines.sumOf { line -> line.maxOf { it.height } } + gap * (lines.size - 1)
        assertEquals(expectedHeight, flex.height, "box is its lines and gaps at width $width, scale $fontScale")
        lines.forEach { line ->
            val top = line.first().y
            line.forEach { assertEquals(top, it.y, "a line's items start together at width $width, scale $fontScale") }
        }
    }

    @Test
    fun aLabelThatFitsStaysOnOneLine() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            Text("Health bar", Modifier.testTag("reference"))
            FlexBox(Modifier.width(width.dp), config = { wrapWithGap() }) { mixed() }
        }

        val reference = nodes.getValue("reference")
        if (reference.width <= width) {
            val label = nodes.getValue("health")
            assertEquals(reference.width, label.width, "width $width, scale $fontScale")
            assertEquals(reference.height, label.height, "a two-word label sized to its own width wraps at width $width, scale $fontScale")
        }
    }

    @Test
    fun centredContentLeavesEqualSpaceAboveAndBelow() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.size(width.dp, 600.dp).testTag("flex"), config = {
                wrapWithGap()
                alignContent = FlexAlignContent.Center
            }) { mixed() }
        }

        val flex = nodes.getValue("flex")
        val items = itemTags.map(nodes::getValue)
        assertNoOverlap(items, "width $width, scale $fontScale")
        val top = items.minOf { it.y } - flex.y
        val bottom = flex.y + flex.height - items.maxOf { it.y + it.height }
        assertTrue(abs(top - bottom) <= 1, "centred: $top above, $bottom below at width $width, scale $fontScale")
    }

    @Test
    fun endAlignedContentEndsAtTheBottomEdge() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.size(width.dp, 600.dp).testTag("flex"), config = {
                wrapWithGap()
                alignContent = FlexAlignContent.End
            }) { mixed() }
        }

        val flex = nodes.getValue("flex")
        val items = itemTags.map(nodes::getValue)
        assertEquals(flex.y + flex.height, items.maxOf { it.y + it.height }, "width $width, scale $fontScale")
    }

    @Test
    fun stretchedLinesFillTheBoxExactly() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.size(width.dp, 600.dp).testTag("flex"), config = {
                wrapWithGap()
                alignContent = FlexAlignContent.Stretch
                alignItems = FlexAlignItems.End
            }) { mixed() }
        }

        val flex = nodes.getValue("flex")
        val items = itemTags.map(nodes::getValue)
        assertNoOverlap(items, "width $width, scale $fontScale")
        items.forEach { assertInside(flex, it, "width $width, scale $fontScale") }
        // End-aligned in the last stretched line, so the lowest item meets the box's bottom edge.
        assertEquals(flex.y + flex.height, items.maxOf { it.y + it.height }, "width $width, scale $fontScale")
    }

    @Test
    fun aStretchedItemTakesItsLineHeightFromTheLabel() = everyCase { _, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.width(400.dp), config = { alignItems = FlexAlignItems.Stretch }) {
                Text("Two\nlines", Modifier.testTag("label"))
                Box(Modifier.width(8.dp).testTag("bar"))
            }
        }

        assertEquals(nodes.getValue("label").height, nodes.getValue("bar").height, "scale $fontScale")
    }

    @Test
    fun centredItemsCentreOnAMultiLineLabel() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.width(width.dp), config = { alignItems = FlexAlignItems.Center }) {
                Text("Two\nlines", Modifier.testTag("label"))
                Box(Modifier.size(10.dp).testTag("icon"))
            }
        }

        val label = nodes.getValue("label")
        val icon = nodes.getValue("icon")
        val labelCentre = label.y * 2 + label.height
        val iconCentre = icon.y * 2 + icon.height
        assertTrue(abs(labelCentre - iconCentre) <= 1, "label centre ${labelCentre / 2}, icon centre ${iconCentre / 2}")
        assertNoOverlap(listOf(label, icon), "width $width, scale $fontScale")
    }

    @Test
    fun aColumnStacksLabelsByTheirHeight() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.width(width.dp).testTag("flex"), config = {
                direction = FlexDirection.Column
                gap(gap.dp)
            }) { mixed() }
        }

        val flex = nodes.getValue("flex")
        val items = itemTags.map(nodes::getValue)
        items.forEach { assertTrue(it.height > 0, "${it.testTag} has a height at width $width, scale $fontScale") }
        assertNoOverlap(items, "width $width, scale $fontScale")
        assertEquals(items.sumOf { it.height } + gap * (items.size - 1), flex.height, "width $width, scale $fontScale")
    }

    @Test
    fun aWrappingColumnStartsANewColumnByMeasuredHeight() = everyCase { _, fontScale ->
        val nodes = frame(fontScale) { Text("One", Modifier.testTag("reference")) }
        val oneLine = nodes.getValue("reference").height
        // Room for two labels and a gap, not three.
        val limit = oneLine * 2 + gap
        val wrapped = frame(fontScale) {
            FlexBox(Modifier.size(400.dp, limit.dp).testTag("flex"), config = {
                direction = FlexDirection.Column
                wrap = FlexWrap.Wrap
                gap(gap.dp)
            }) {
                Text("One", Modifier.testTag("a"))
                Text("Two", Modifier.testTag("b"))
                Text("Three", Modifier.testTag("c"))
            }
        }

        val a = wrapped.getValue("a")
        val b = wrapped.getValue("b")
        val c = wrapped.getValue("c")
        assertEquals(a.x, b.x, "the first two share a column at scale $fontScale")
        assertEquals(a.y + a.height + gap, b.y, "scale $fontScale")
        assertEquals(a.y, c.y, "the third starts the next column at scale $fontScale")
        assertTrue(c.x >= a.x + a.width + gap, "scale $fontScale")
    }

    @Test
    fun intrinsicHeightOfAWrappingBoxIsItsLinesAndGaps() = everyCase { width, fontScale ->
        val nodes = frame(fontScale) {
            FlexBox(Modifier.width(width.dp).testTag("natural"), config = { wrapWithGap() }) { mixed() }
            FlexBox(Modifier.width(width.dp).height(IntrinsicSize.Max).testTag("intrinsicMax"), config = { wrapWithGap() }) {
                mixed(prefix = "max-")
            }
            FlexBox(Modifier.width(width.dp).height(IntrinsicSize.Min).testTag("intrinsicMin"), config = { wrapWithGap() }) {
                mixed(prefix = "min-")
            }
        }

        val natural = nodes.getValue("natural").height
        assertEquals(natural, nodes.getValue("intrinsicMax").height, "max at width $width, scale $fontScale")
        assertEquals(natural, nodes.getValue("intrinsicMin").height, "min at width $width, scale $fontScale")
    }

    @Test
    fun intrinsicWidthsFollowTheDirectionAndWrap() = everyCase { _, fontScale ->
        val nodes = frame(fontScale) {
            Text("Stamina", Modifier.testTag("widestWord"))
            Text("Stamina restores slowly over time", Modifier.testTag("widestLine"))
            FlexBox(Modifier.width(IntrinsicSize.Max).testTag("column"), config = {
                direction = FlexDirection.Column
                gap(gap.dp)
            }) {
                Text("Stamina restores slowly over time")
                Box(Modifier.size(24.dp))
            }
            FlexBox(Modifier.width(IntrinsicSize.Min).testTag("wrapMin"), config = { wrapWithGap() }) {
                Text("Stamina restores")
                Box(Modifier.size(24.dp))
            }
            FlexBox(Modifier.width(IntrinsicSize.Max).testTag("rowMax"), config = { wrapWithGap() }) {
                Text("Stamina", Modifier.testTag("rowLabel"))
                Box(Modifier.size(24.dp))
            }
        }

        val widestLine = nodes.getValue("widestLine").width
        val widestWord = nodes.getValue("widestWord").width
        assertEquals(widestLine, nodes.getValue("column").width, "a column is as wide as its widest item, scale $fontScale")
        assertEquals(maxOf(widestWord, 24), nodes.getValue("wrapMin").width, "a wrapping row's minimum is its widest minimum, scale $fontScale")
        assertEquals(widestWord + gap + 24, nodes.getValue("rowMax").width, "a row's maximum is one line, scale $fontScale")
    }

    private val itemTags = listOf("health", "icon", "stamina", "tall", "twoLines", "flat")

    context(_: Composer)
    private fun FlexBoxScope.mixed(prefix: String = "") {
        Text("Health bar", Modifier.testTag("${prefix}health"))
        Box(Modifier.size(24.dp).testTag("${prefix}icon"))
        Text("Stamina restores slowly over time", Modifier.testTag("${prefix}stamina"))
        Box(Modifier.size(16.dp, 40.dp).testTag("${prefix}tall"))
        Text("Two\nlines", Modifier.testTag("${prefix}twoLines"))
        Box(Modifier.size(30.dp, 12.dp).testTag("${prefix}flat"))
    }

    private fun FlexBoxConfigScope.wrapWithGap() {
        wrap = FlexWrap.Wrap
        gap(gap.dp)
    }

    private fun everyCase(check: (width: Int, fontScale: Float) -> Unit) {
        for (fontScale in fontScales) for (width in widths) check(width, fontScale)
    }

    /** Items grouped by the line they were placed on: a new line starts wherever x goes back. */
    private fun linesOf(items: List<SemanticsNode>): List<List<SemanticsNode>> {
        val lines = mutableListOf<MutableList<SemanticsNode>>()
        var lastX = Int.MAX_VALUE
        for (item in items) {
            if (item.x <= lastX) lines += mutableListOf<SemanticsNode>()
            lines.last() += item
            lastX = item.x
        }
        return lines
    }

    private fun assertNoOverlap(items: List<SemanticsNode>, case: String) {
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val a = items[i]
                val b = items[j]
                val overlaps = a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
                assertTrue(!overlaps, "${a.testTag} $a overlaps ${b.testTag} $b at $case")
            }
        }
    }

    private fun assertInside(box: SemanticsNode, item: SemanticsNode, case: String) {
        val inside = item.x >= box.x && item.y >= box.y &&
            item.x + item.width <= box.x + box.width && item.y + item.height <= box.y + box.height
        assertTrue(inside, "${item.testTag} $item leaves the box $box at $case")
    }

    private fun frame(fontScale: Float, content: context(Composer) () -> Unit): Map<String, SemanticsNode> {
        val frame = ComposeHost(density = 1f, fontScale = fontScale)
            .frame(FrameInput(viewportWidth = 1200, viewportHeight = 1200), content)
        val all = mutableListOf<SemanticsNode>()
        fun collect(values: List<SemanticsNode>) {
            values.forEach {
                all += it
                collect(it.children)
            }
        }
        collect(frame.semantics)
        return all.filter { it.testTag != null }.associateBy { it.testTag!! }
    }
}
