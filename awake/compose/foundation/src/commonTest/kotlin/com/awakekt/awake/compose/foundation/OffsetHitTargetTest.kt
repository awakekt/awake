/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.input.pointer.PointerInputDispatcher
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An offset node is clickable where it is drawn, and only there.
 *
 * `Modifier.offset` reports its child's size and then places it somewhere else, so the node's own
 * box stays where its parent put it while everything inside -- every draw, every pointer link --
 * is elsewhere. The two boxes never overlapped, and the hit-test gate tested only the first: an
 * offset node could not be clicked at the position it was drawn at *or* the one it was placed at.
 * Silently, because nothing about a modifier that moves things suggests it also removes them from
 * the input tree.
 *
 * Found while building the tooltip, whose first test positioned its trigger with `offset` and
 * could not hover it.
 */
class OffsetHitTargetTest {

    private fun clicksAt(x: Int, y: Int): Int {
        var clicks = 0
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            Box(Modifier.offset(y = OFFSET.dp).size(SIZE.dp).clickable { clicks++ }) {}
        }
        root.layoutTree(Constraints.of(200, 200, 200, 200))

        val dispatcher = PointerInputDispatcher()
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), x, y)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), x, y)
        return clicks
    }

    @Test
    fun anOffsetNodeIsClickableWhereItIsDrawn() {
        assertEquals(1, clicksAt(SIZE / 2, OFFSET + SIZE / 2))
    }

    @Test
    fun anOffsetNodeIsNotClickableWhereItWouldHaveBeen() {
        // The other half of the rule, and the reason the gate is a union rather than a swap:
        // widening it must not make the vacated space clickable.
        assertEquals(0, clicksAt(SIZE / 2, SIZE / 2))
    }

    private companion object {
        const val OFFSET = 80
        const val SIZE = 24
    }
}
