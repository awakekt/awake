/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.draw.zIndex
import com.awakekt.awake.compose.ui.input.pointer.pointerCursor
import com.awakekt.awake.compose.ui.layout.layoutId
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.PointerCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A composable that hands a new modifier each frame almost always hands the same links with new
 * values. Re-setting such a modifier updated the retained nodes but also cleared and refilled every
 * per-kind list of the chain; it now keeps the chain and reads again only what the nodes report.
 */
class ModifierChainInPlaceTest {
    private fun marked(x: Int, z: Float, id: String, cursor: PointerCursor): Modifier =
        Modifier.offset(x = x.dp).size(10.dp).zIndex(z).layoutId(id).pointerCursor(cursor)

    @Test
    fun aModifierOfTheSameShapeKeepsTheChainAndTakesTheNewValues() {
        val node = LayoutNode(ColumnMeasurePolicy())
        node.modifier = marked(1, 1f, "first", PointerCursor.Default)
        val rebuilds = node.chainRebuilds

        node.modifier = marked(2, 2f, "second", PointerCursor.ResizeHorizontal)

        assertEquals(rebuilds, node.chainRebuilds, "the chain was rebuilt")
        assertEquals(2f, node.zIndex)
        assertEquals("second", node.layoutId)
        assertEquals(PointerCursor.ResizeHorizontal, node.pointerCursor)
    }

    @Test
    fun aModifierOfAnotherShapeRebuildsTheChain() {
        val node = LayoutNode(ColumnMeasurePolicy())
        node.modifier = marked(1, 1f, "first", PointerCursor.Default)
        val rebuilds = node.chainRebuilds

        node.modifier = Modifier.offset(x = 1.dp).size(10.dp)

        assertEquals(rebuilds + 1, node.chainRebuilds)
        assertEquals(0f, node.zIndex)
        assertNull(node.layoutId)
        assertNull(node.pointerCursor)
    }

    @Test
    fun aLongerChainOfTheSamePrefixRebuildsTheChain() {
        val node = LayoutNode(ColumnMeasurePolicy())
        node.modifier = Modifier.size(10.dp)
        val rebuilds = node.chainRebuilds

        node.modifier = Modifier.size(10.dp).zIndex(3f)

        assertEquals(rebuilds + 1, node.chainRebuilds)
        assertEquals(3f, node.zIndex)
    }

    @Test
    fun settingEmptyModifierOnEmptyNodeDoesNotRebuildTheChain() {
        val node = LayoutNode(ColumnMeasurePolicy())
        node.modifier = Modifier
        node.modifier = Modifier

        assertEquals(0, node.chainRebuilds)
    }

    @Test
    fun reapplyingTheSameModifierInstanceDoesNotRebuildTheChain() {
        val node = LayoutNode(ColumnMeasurePolicy())
        val shared = marked(1, 1f, "shared", PointerCursor.Default)
        node.modifier = shared
        val rebuilds = node.chainRebuilds

        node.modifier = shared

        assertEquals(rebuilds, node.chainRebuilds)
    }
}
