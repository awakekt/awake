/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder.layout

import com.awakekt.awake.ui.builder.model.UiNode

/**
 * Calculated drop target for drag-and-drop operations on the canvas.
 */
data class DropTarget(
    val parentNodeId: String,
    val insertionIndex: Int,
    val containerType: String,
)

/**
 * Spatial calculator that determines insertion points for drag gestures.
 */
object DropTargetSolver {
    /**
     * Solves the drop target index given a parent container node and pointer Y position / child count.
     */
    fun solveIndex(
        parent: UiNode,
        cursorY: Float,
        childBoundsY: List<Float>,
    ): DropTarget {
        var targetIndex = parent.children.size
        for (i in childBoundsY.indices) {
            if (cursorY < childBoundsY[i]) {
                targetIndex = i
                break
            }
        }
        return DropTarget(
            parentNodeId = parent.id,
            insertionIndex = targetIndex,
            containerType = parent.type,
        )
    }
}
