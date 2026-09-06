/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder

import com.awakekt.awake.ui.builder.layout.DropTargetSolver
import com.awakekt.awake.ui.builder.model.UiNode
import kotlin.test.Test
import kotlin.test.assertEquals

class DropTargetSolverTest {
    @Test
    fun calculatesCorrectInsertionIndex() {
        val root = UiNode(
            id = "root",
            type = "Container.Column",
            children = listOf(
                UiNode(id = "c1", type = "Widget.ShadcnButton"),
                UiNode(id = "c2", type = "Widget.ShadcnButton"),
            ),
        )
        val childY = listOf(100f, 200f)

        val target1 = DropTargetSolver.solveIndex(root, cursorY = 50f, childBoundsY = childY)
        assertEquals(0, target1.insertionIndex)

        val target2 = DropTargetSolver.solveIndex(root, cursorY = 150f, childBoundsY = childY)
        assertEquals(1, target2.insertionIndex)

        val target3 = DropTargetSolver.solveIndex(root, cursorY = 250f, childBoundsY = childY)
        assertEquals(2, target3.insertionIndex)
    }
}
