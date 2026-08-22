// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.key
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layer
import io.github.ronjunevaldoz.awake.compose.ui.layout.LayerKind
import io.github.ronjunevaldoz.awake.compose.ui.layout.Layout
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

private object BoxType

private object TextType

private val passthrough = MeasurePolicy { measurables, constraints ->
    var width = 0
    var height = 0
    val placeables = measurables.map { it.measure(constraints) }
    placeables.forEach {
        width = maxOf(width, it.width)
        height = maxOf(height, it.height)
    }
    layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
        placeables.forEach { it.placeAt(0, 0) }
    }
}

context(_: Composer)
private fun box(
    content: (
        context(Composer)
        () -> Unit
    )? = null,
) =
    Layout(BoxType, measurePolicy = passthrough, content = content)

context(_: Composer)
private fun text() = Layout(TextType, measurePolicy = passthrough)

private fun root() = LayoutNode(passthrough)

class ReconcilerTest {

    @Test
    fun asecondPassReusesTheSameNodes() {
        // The whole point of a retained tree: node state survives, so anything hung off a node --
        // animation phase, scroll offset -- is not reset every frame the way ui-core resets it.
        val root = root()
        composeInto(root) {
            box()
            box()
        }
        val first = root.children[0]
        val second = root.children[1]

        composeInto(root) {
            box()
            box()
        }

        assertEquals(2, root.children.size)
        assertSame(first, root.children[0])
        assertSame(second, root.children[1])
    }

    @Test
    fun aDifferentTypeAtTheSamePositionIsADifferentNode() {
        val root = root()
        composeInto(root) { box() }
        val original = root.children[0]

        composeInto(root) { text() }

        assertNotSame(original, root.children[0])
        assertEquals(TextType, root.children[0].nodeType)
    }

    @Test
    fun childrenNoLongerDeclaredAreDropped() {
        val root = root()
        composeInto(root) {
            box()
            box()
            box()
        }
        assertEquals(3, root.children.size)

        composeInto(root) { box() }

        assertEquals(1, root.children.size)
    }

    @Test
    fun nestingReconcilesIndependentlyAtEachDepth() {
        val root = root()
        composeInto(root) {
            box {
                text()
                text()
            }
        }
        val outer = root.children[0]
        val innerFirst = outer.children[0]

        composeInto(root) { box { text() } }

        assertSame(outer, root.children[0], "the outer node is unchanged")
        assertSame(innerFirst, outer.children[0])
        assertEquals(1, outer.children.size, "the second inner child was dropped")
    }

    @Test
    fun keyedChildrenSurviveAReorder() {
        // Without keys the node at index 0 stays the node at index 0, so state follows the slot
        // rather than the item -- the failure mirror-map documents for ui-core's id-keyed hooks.
        val root = root()
        composeInto(root) {
            for (id in listOf("a", "b", "c")) key(id) { box() }
        }
        val nodeA = root.children[0]
        val nodeC = root.children[2]

        composeInto(root) {
            for (id in listOf("c", "a", "b")) key(id) { box() }
        }

        assertSame(nodeC, root.children[0], "c moved to the front, it was not rebuilt")
        assertSame(nodeA, root.children[1])
        assertEquals(listOf("c", "a", "b"), root.children.asList().map { it.nodeKey })
    }

    @Test
    fun unkeyedChildrenDoNotAdoptEachOther() {
        // A forward scan for an unkeyed match would let an unrelated node of the same type be
        // adopted after a removal, which is worse than rebuilding it.
        val root = root()
        composeInto(root) {
            box()
            box()
        }
        val second = root.children[1]

        composeInto(root) { box() }

        assertEquals(1, root.children.size)
        assertEquals(false, root.children.asList().contains(second))
    }

    @Test
    fun layersGoToTheirOwnSlotNotTheChildList() {
        val root = root()
        composeInto(root) {
            box()
            Layer(LayerKind.Popup, measurePolicy = passthrough)
        }

        assertEquals(1, root.children.size, "the popup is not a laid-out child")
        assertEquals(1, root.layers.size)
        assertEquals(LayerKind.Popup, root.layers[0].nodeType)
    }

    @Test
    fun aLayerStaysWithTheNodeThatDeclaredIt() {
        // Anchoring needs the declaring node, so a layer is a slot on that node rather than a flat
        // list hoisted to the root.
        val root = root()
        composeInto(root) {
            box {
                Layer(LayerKind.Tooltip, measurePolicy = passthrough)
            }
        }

        assertEquals(0, root.layers.size)
        assertEquals(1, root.children[0].layers.size)
    }

    @Test
    fun aLayerIsInvisibleToTheParentsMeasurePolicy() {
        val root = root()
        composeInto(root) {
            Layout(BoxType, modifier = Modifier.size(30.dp), measurePolicy = passthrough)
            Layer(LayerKind.Dialog, modifier = Modifier.size(500.dp), measurePolicy = passthrough)
        }
        root.measure(Constraints.of(0, 200, 0, 200))

        assertEquals(30, root.width, "the 500dp dialog did not widen its parent")
    }

    @Test
    fun updatesReachAReusedNode() {
        // Identity surviving must not mean stale configuration: a modifier can change without the
        // node changing.
        val root = root()
        composeInto(root) { Layout(BoxType, modifier = Modifier.size(10.dp), measurePolicy = passthrough) }
        val node = root.children[0]

        composeInto(root) { Layout(BoxType, modifier = Modifier.size(40.dp), measurePolicy = passthrough) }
        root.measure(Constraints.of(0, 200, 0, 200))

        assertSame(node, root.children[0])
        assertEquals(40, node.width)
    }
}
