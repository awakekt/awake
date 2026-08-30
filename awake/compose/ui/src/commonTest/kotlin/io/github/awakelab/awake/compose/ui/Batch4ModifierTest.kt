/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.focus.focusTarget
import io.github.awakelab.awake.compose.ui.layout.Layout
import io.github.awakelab.awake.compose.ui.layout.MeasurePolicy
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layout
import io.github.awakelab.awake.compose.ui.layout.layoutId
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.layout.onSizeChanged
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsProperties
import io.github.awakelab.awake.compose.ui.semantics.clearAndSetSemantics
import io.github.awakelab.awake.compose.ui.semantics.semantics
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object B4Type

private val b4Stack = MeasurePolicy { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    val w = placeables.maxOfOrNull { it.width } ?: 0
    val h = placeables.maxOfOrNull { it.height } ?: 0
    layout(constraints.constrainWidth(w), constraints.constrainHeight(h)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun b4(
    modifier: Modifier,
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(B4Type, modifier = modifier, measurePolicy = b4Stack, content = content)

private fun tree4(content: context(Composer) () -> Unit): LayoutNode {
    val root = LayoutNode(b4Stack)
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return root
}

class LayoutModifierTest {

    @Test
    fun itMeasuresAndPlacesHoweverTheBlockSays() {
        val root = tree4 {
            b4(
                Modifier.size(40.dp).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // Report half the width the content asked for.
                    layout(placeable.width / 2, placeable.height) { placeable.placeAt(0, 0) }
                },
            )
        }

        assertEquals(20, root.children[0].width)
        assertEquals(40, root.children[0].height)
    }
}

class LayoutIdTest {

    @Test
    fun aPolicyCanTellChildrenApartByName() {
        // The alternative is positional -- "the second measurable is the label" -- which silently
        // means something else the moment a caller reorders or conditionally omits a child.
        val seen = mutableListOf<Any?>()
        val naming = MeasurePolicy { measurables, constraints ->
            measurables.forEach { seen += it.layoutId }
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { it.placeAt(0, 0) }
            }
        }
        val root = LayoutNode(naming)
        composeInto(root) {
            b4(Modifier.size(10.dp).layoutId("label"))
            b4(Modifier.size(10.dp).layoutId("value"))
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(listOf<Any?>("label", "value"), seen)
    }

    @Test
    fun anUntaggedChildReportsNull() {
        val seen = mutableListOf<Any?>()
        val naming = MeasurePolicy { measurables, constraints ->
            measurables.forEach { seen += it.layoutId }
            layout(0, 0) { }
        }
        val root = LayoutNode(naming)
        composeInto(root) { b4(Modifier.size(10.dp)) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(listOf<Any?>(null), seen)
    }
}

class OnSizeChangedTest {

    @Test
    fun itReportsOnceRatherThanEveryPass() {
        // onPlaced fires after every layout pass. A caller that only wants the size would otherwise
        // get a callback per frame forever, for a size that never moved.
        val sizes = mutableListOf<String>()
        val root = LayoutNode(b4Stack)
        val content: context(Composer)
        () -> Unit = {
            b4(Modifier.size(30.dp).onSizeChanged { w, h -> sizes += "${w}x$h" })
        }
        composeInto(root, content)
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(listOf("30x30"), sizes)
    }

    @Test
    fun aRealChangeIsReported() {
        val sizes = mutableListOf<String>()
        val root = LayoutNode(b4Stack)
        composeInto(root) { b4(Modifier.size(30.dp).onSizeChanged { w, h -> sizes += "${w}x$h" }) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        composeInto(root) { b4(Modifier.size(50.dp).onSizeChanged { w, h -> sizes += "${w}x$h" }) }
        root.layoutTree(Constraints.of(0, 200, 0, 200))

        assertEquals(listOf("30x30", "50x50"), sizes)
    }
}

class ClearAndSetSemanticsTest {

    @Test
    fun descendantsAreReplacedRatherThanAbsorbed() {
        // A chart of a hundred labelled bars should report "revenue by month", not a hundred bars,
        // and not "revenue by month" with a bar's label leaking in to fill a gap.
        val root = tree4 {
            b4(
                Modifier.size(50.dp).clearAndSetSemantics {
                    this[SemanticsProperties.Label] = "chart"
                },
            ) {
                b4(Modifier.size(10.dp).semantics { this[SemanticsProperties.TestTag] = "bar" })
            }
        }
        val nodes = SemanticsTreeBuilderAccess.build(root)

        assertEquals(1, nodes.size, "the descendants were not cleared")
        assertEquals("chart", nodes[0].config[SemanticsProperties.Label])
        assertNull(nodes[0].config[SemanticsProperties.TestTag], "a descendant's tag leaked in")
    }

    @Test
    fun mergeStillAbsorbs() {
        // The contrast that makes the test above meaningful: plain merge fills its gaps.
        val root = tree4 {
            b4(Modifier.size(50.dp).semantics(mergeDescendants = true) { }) {
                b4(Modifier.size(10.dp).semantics { this[SemanticsProperties.TestTag] = "bar" })
            }
        }
        val nodes = SemanticsTreeBuilderAccess.build(root)

        assertEquals("bar", nodes[0].config[SemanticsProperties.TestTag])
    }
}

class FocusTargetTest {

    @Test
    fun itCanTakeFocus() {
        val root = tree4 { b4(Modifier.size(20.dp).focusTarget()) }
        val owner = io.github.awakelab.awake.compose.ui.focus.FocusOwner()

        assertTrue(owner.requestFocus(root, root.children[0]))
    }

    @Test
    fun disabledStaysOutOfTheRing() {
        val root = tree4 { b4(Modifier.size(20.dp).focusTarget(enabled = false)) }
        val owner = io.github.awakelab.awake.compose.ui.focus.FocusOwner()

        assertTrue(!owner.requestFocus(root, root.children[0]))
    }
}

private object SemanticsTreeBuilderAccess {
    fun build(root: LayoutNode) =
        io.github.awakelab.awake.compose.ui.semantics.SemanticsTreeBuilder().build(root)
}
