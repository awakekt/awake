/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.semantics

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private object ProbeType

private val column = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    layout(constraints.maxWidth, constraints.maxHeight) {
        var y = 0
        placeables.forEach { it.placeAt(0, y); y += it.height }
    }
}

/**
 * A frame that composes its semantics again, with the same values, hands back the tree it built
 * before instead of merging every node's configuration anew (#1742): the default host composes
 * every frame, and a HUD's test tags were rebuilt and re-merged on each one.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class SemanticsComposedFrameTest {
    private val input = FrameInput(320, 2400, deltaSeconds = 0f)

    private fun bytes(tagged: Boolean, merging: Boolean): Long {
        val host = ComposeHost()
        fun frame() = host.frame(input) { content(tagged, merging) }
        repeat(WARMUP) { frame() }
        return readAllocation(WINDOW) { repeat(WINDOW) { frame() } }.perUnit
    }

    context(_: Composer)
    private fun content(tagged: Boolean, merging: Boolean) {
        Layout(ProbeType, modifier = Modifier, measurePolicy = column, content = {
            for (i in 0 until ITEMS) row(i, tagged, merging)
        })
    }

    context(_: Composer)
    private fun row(i: Int, tagged: Boolean, merging: Boolean) {
        val outer = if (!tagged) {
            Modifier.size(40.dp, 20.dp)
        } else if (merging) {
            Modifier.size(40.dp, 20.dp).semantics(mergeDescendants = true) { this[SemanticsProperties.TestTag] = TAGS[i] }
        } else {
            Modifier.size(40.dp, 20.dp).testTag(TAGS[i])
        }
        Layout(ProbeType, modifier = outer, measurePolicy = column, content = {
            val inner = if (tagged) Modifier.size(10.dp).testTag(LABELS[i]) else Modifier.size(10.dp)
            Layout(ProbeType, modifier = inner, measurePolicy = column, content = null)
        })
    }

    @Test
    fun aRecomposedFrameWithTheSameSemanticsHandsBackItsTree() {
        val host = ComposeHost()
        fun frame() = host.frame(input) { content(tagged = true, merging = false) }.semantics
        val first = frame()
        assertSame(first, frame(), "a recomposed frame with equal semantics built a new tree")
    }

    @Test
    fun aRecomposedFrameReportsAChangedTag() {
        val host = ComposeHost()
        var tag = "before"
        fun tags() = host.frame(input) {
            Layout(ProbeType, modifier = Modifier.size(10.dp).testTag(tag), measurePolicy = column, content = null)
        }.semantics.map { it.testTag }
        assertEquals(listOf("before"), tags())
        tag = "after"
        assertEquals(listOf("after"), tags())
    }

    @Test
    fun semanticsCostAComposedFrameLittleMoreThanItsModifiers() {
        val plain = bytes(tagged = false, merging = false)
        for (merging in listOf(false, true)) {
            val perNode = (bytes(tagged = true, merging) - plain) / NODES
            println("PERF semantics composed frame merging=$merging: $perNode B a node over plain")
            val ceiling = if (merging) NODE_CEILING_BYTES else TEST_TAG_CEILING_BYTES
            assertTrue(perNode <= ceiling, "semantics cost $perNode B a node a composed frame (merging=$merging)")
        }
    }

    @Test
    fun buildSemanticsFalseReturnsEmptyAndRebuildsWhenReEnabled() {
        val host = ComposeHost()
        val withSemantics = host.frame(input) { content(tagged = true, merging = false) }.semantics
        assertEquals(ITEMS, withSemantics.size)

        host.buildSemantics = false
        val skipped = host.frame(input) { content(tagged = true, merging = false) }.semantics
        assertTrue(skipped.isEmpty(), "expected empty semantics when buildSemantics=false")

        // Moving or mutating tags while buildSemantics=false stays empty without allocating semantics nodes.
        var counter = 0
        repeat(WARMUP) {
            counter++
            val frame = host.frame(input) {
                Layout(ProbeType, modifier = Modifier.size(10.dp).testTag("t-$counter"), measurePolicy = column, content = null)
            }
            assertTrue(frame.semantics.isEmpty())
        }

        host.buildSemantics = true
        val rebuilt = host.frame(input) {
            Layout(ProbeType, modifier = Modifier.size(10.dp).testTag("t-$counter"), measurePolicy = column, content = null)
        }.semantics
        assertEquals(listOf("t-$counter"), rebuilt.map { it.testTag })
    }

    private companion object {
        const val ITEMS = 50
        const val NODES = ITEMS * 2

        // The configuration, its entries and the modifier element a composition builds: 112 B.
        // Rebuilding and re-merging the tree every frame cost 605 B a node.
        const val NODE_CEILING_BYTES = 160L

        // A test tag's element and its link in the chain: 39 B. Building a configuration, its
        // entries and a block for every tag at every composition cost 111 B.
        const val TEST_TAG_CEILING_BYTES = 48L
        const val WARMUP = 300
        const val WINDOW = 50
        val TAGS = List(ITEMS) { "row$it" }
        val LABELS = List(ITEMS) { "label$it" }
    }
}
