// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Reconciliation is "match this pass's declarations to last pass's nodes" and has nothing to do
 * with layout. These run it against a tree of plain objects -- if any of them needed a `LayoutNode`,
 * `:awake:compose:runtime` would not deserve to be a module of its own.
 */
class ComposerTest {

    private fun compose(root: FakeNode, content: context(Composer) () -> Unit) {
        reconcile(FakeApplier(root), content)
    }

    @Test
    fun nodesAreReusedAcrossPasses() {
        val root = FakeNode("root", null)
        compose(root) {
            node("a")
            node("b")
        }
        val first = root.children[0]

        compose(root) {
            node("a")
            node("b")
        }

        assertEquals(2, root.children.size)
        assertSame(first, root.children[0])
    }

    @Test
    fun updateRunsOnEveryPassEvenWhenTheNodeIsReused() {
        // A reused node can still be misconfigured -- identity surviving is not the same as
        // configuration surviving.
        val root = FakeNode("root", null)
        val bump: (Any) -> Unit = { (it as FakeNode).updates++ }
        compose(root) { node("a", update = bump) }
        compose(root) { node("a", update = bump) }

        assertEquals(2, root.children[0].updates)
    }

    @Test
    fun aChangedTypeReplacesTheNode() {
        val root = FakeNode("root", null)
        compose(root) { node("a") }
        val original = root.children[0]

        compose(root) { node("b") }

        assertEquals("b", root.children[0].type)
        assertEquals(false, root.children.contains(original))
    }

    @Test
    fun undeclaredChildrenAreDroppedAtEveryDepth() {
        val root = FakeNode("root", null)
        compose(root) {
            node("a") {
                node("x")
                node("y")
            }
        }
        assertEquals(2, root.children[0].children.size)

        compose(root) { node("a") { node("x") } }

        assertEquals(1, root.children[0].children.size)
    }

    @Test
    fun keysSurviveAReorder() {
        val root = FakeNode("root", null)
        compose(root) { for (id in listOf("a", "b", "c")) key(id) { node("item") } }
        val nodeC = root.children[2]

        compose(root) { for (id in listOf("c", "a", "b")) key(id) { node("item") } }

        assertSame(nodeC, root.children[0])
        assertEquals(listOf("c", "a", "b"), root.children.map { it.key })
    }

    @Test
    fun aKeyAppliesToEveryNodeDeclaredInsideIt() {
        val root = FakeNode("root", null)
        compose(root) {
            key("group") {
                node("a")
                node("b")
            }
        }

        assertEquals(listOf("group", "group"), root.children.map { it.key })
    }

    @Test
    fun layersReconcileInTheirOwnIndexSpace() {
        // Children and layers each count from zero, so declaring one does not shift the other.
        val root = FakeNode("root", null)
        compose(root) {
            node("child")
            node("overlay", slot = Slot.Layers)
            node("child2")
        }

        assertEquals(listOf("child", "child2"), root.children.map { it.type })
        assertEquals(listOf("overlay"), root.layers.map { it.type })
    }

    @Test
    fun deepNestingDoesNotOverflowTheCursorStack() {
        // The cursor arrays start at 16 deep and grow; a tree deeper than that must still work.
        val root = FakeNode("root", null)
        fun nest(depth: Int): context(Composer)
        () -> Unit = {
            if (depth == 0) node("leaf") else node("branch", content = nest(depth - 1))
        }
        compose(root, nest(40))

        var node = root
        var depth = 0
        while (node.children.isNotEmpty()) {
            node = node.children[0]
            depth++
        }
        assertEquals(41, depth)
    }
}
