/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.SemanticsTreeBuilder
import com.awakekt.awake.compose.ui.semantics.semantics
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

private object ReuseType

private val stacked = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
    val h = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun node(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(ReuseType, modifier = modifier, measurePolicy = stacked, content = content)

/**
 * [SemanticsTreeBuilder] hands back its last tree while nothing it was built from changed, and keeps
 * its merged configurations while only bounds moved (#1512). A skipped frame asks for the tree every
 * frame, so the reuse must be invisible: each case also builds afresh and compares.
 */
class SemanticsReuseTest {
    private val root = LayoutNode(stacked)
    private val builder = SemanticsTreeBuilder()

    private fun lay(content: context(Composer) () -> Unit) {
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 200, 0, 200))
    }

    private fun fresh(): List<SemanticsNode> = SemanticsTreeBuilder().build(root)

    private fun List<SemanticsNode>.shape(): String =
        joinToString { "${it.testTag ?: it.label}@${it.x},${it.y} ${it.width}x${it.height}[${it.children.shape()}]" }

    private val panel: context(Composer)
    () -> Unit = {
        node(Modifier.padding(10.dp).testTag("panel")) {
            node(Modifier.size(30.dp).testTag("bar"))
        }
        node(Modifier.size(20.dp).semantics(mergeDescendants = true) { this[SemanticsProperties.Role] = null }) {
            node(Modifier.size(10.dp).semantics { this[SemanticsProperties.Label] = "hp" })
        }
    }

    private fun tagged(tag: String): LayoutNode {
        fun find(node: LayoutNode): LayoutNode? {
            if (node.semanticsModifiers.any { it.semanticsConfiguration[SemanticsProperties.TestTag] == tag }) return node
            for (i in 0 until node.children.size) find(node.children[i])?.let { return it }
            return null
        }
        return find(root)!!
    }

    @Test
    fun anUnchangedTreeIsHandedBackAsItWas() {
        lay(panel)
        val first = builder.build(root)
        assertSame(first, builder.build(root), "nothing changed, so nothing is built")
        assertEquals(fresh().shape(), first.shape())
    }

    @Test
    fun aMovedNodeIsReportedAtItsNewPlaceWithTheSameProperties() {
        lay(panel)
        val first = builder.build(root)
        val bar = tagged("bar")
        bar.absoluteX += 7
        val moved = builder.build(root)
        assertNotSame(first, moved)
        assertEquals(fresh().shape(), moved.shape(), "the reused tree reads as a fresh one")
        assertEquals(first[0].children[0].x + 7, moved[0].children[0].x)
        assertSame(first[0].children[0].config, moved[0].children[0].config, "the merged configuration is kept")
    }

    @Test
    fun aMergingNodesAbsorbedChildMovingChangesNothing() {
        lay(panel)
        val first = builder.build(root)
        val merging = root.children[1]
        merging.children[0].absoluteX += 3
        assertSame(first, builder.build(root), "an absorbed node reports no bounds of its own")
        assertEquals("hp", first[1].label)
    }

    @Test
    fun recomposedContentIsBuiltAfresh() {
        lay(panel)
        val first = builder.build(root)
        lay { node(Modifier.size(30.dp).testTag("other")) }
        val next = builder.build(root)
        assertNotSame(first, next)
        assertEquals(fresh().shape(), next.shape())
        assertEquals("other", next.single().testTag)
    }
}
