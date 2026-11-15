/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A host hands [CanvasOverlay] a new item list each frame, almost always of the same elements under
 * the same parents. The hierarchy was built again each frame, sorting and filling maps; it is now
 * kept until the items' structure changes, and the same lists are handed out while it holds.
 */
class CanvasStructureTest {
    private val panel = CanvasElement()
    private val first = CanvasElement().apply { order = 2 }
    private val second = CanvasElement().apply { order = 1 }

    private fun items() = listOf(CanvasItem(1, panel), CanvasItem(2, first, panel), CanvasItem(3, second, panel))

    @Test
    fun anUnchangedCanvasKeepsItsHierarchy() {
        val structure = CanvasStructure()
        structure.update(items())
        val roots = structure.roots
        val children = structure.childrenOf(panel)

        structure.update(items())

        assertEquals(1, structure.builds)
        assertSame(roots, structure.roots)
        assertSame(children, structure.childrenOf(panel))
        assertEquals(listOf(second, first), children.map { it.element })
    }

    @Test
    fun aChangedOrderBuildsNewListsInTheNewOrder() {
        val structure = CanvasStructure()
        structure.update(items())
        val before = structure.childrenOf(panel)

        second.order = 3
        structure.update(items())

        assertEquals(2, structure.builds)
        assertEquals(listOf(second, first), before.map { it.element }, "a handed-out list changed")
        assertEquals(listOf(first, second), structure.childrenOf(panel).map { it.element })
    }

    @Test
    fun aMovedChildBuildsTheHierarchyAgain() {
        val structure = CanvasStructure()
        structure.update(items())

        structure.update(listOf(CanvasItem(1, panel), CanvasItem(2, first, panel), CanvasItem(3, second)))

        assertEquals(2, structure.builds)
        assertEquals(listOf(first), structure.childrenOf(panel).map { it.element })
        assertEquals(listOf(panel, second), structure.roots.map { it.element })
    }

    @Test
    fun anElementListedByTwoCanvasesFindsItsChildrenInEach() {
        val one = CanvasStructure().apply { update(items()) }
        val other = CanvasStructure().apply { update(listOf(CanvasItem(9, CanvasElement()), CanvasItem(1, panel))) }

        assertEquals(listOf(second, first), one.childrenOf(panel).map { it.element })
        assertEquals(emptyList(), other.childrenOf(panel))
    }
}
