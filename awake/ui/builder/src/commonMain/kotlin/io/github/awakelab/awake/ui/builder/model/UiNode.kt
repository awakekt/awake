/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.awakelab.awake.ui.builder.model

import kotlinx.serialization.Serializable

/**
 * A node in the UI builder layout tree.
 */
@Serializable
data class UiNode(
    val id: String,
    val type: String,
    val props: Map<String, String> = emptyMap(),
    val style: UiStyleSpec = UiStyleSpec(),
    val children: List<UiNode> = emptyList(),
) {
    /** Helper to deep-find a node by its ID. */
    fun findNode(targetId: String): UiNode? {
        if (id == targetId) return this
        for (child in children) {
            val found = child.findNode(targetId)
            if (found != null) return found
        }
        return null
    }

    /** Helper to immutably insert a node into a target parent at a given index. */
    fun insertNode(targetParentId: String, index: Int, newNode: UiNode): UiNode {
        if (id == targetParentId) {
            val updatedChildren = children.toMutableList()
            val safeIndex = index.coerceIn(0, updatedChildren.size)
            updatedChildren.add(safeIndex, newNode)
            return copy(children = updatedChildren)
        }
        return copy(children = children.map { it.insertNode(targetParentId, index, newNode) })
    }

    /** Helper to immutably remove a node by ID. */
    fun removeNode(targetId: String): UiNode {
        val updatedChildren = children
            .filter { it.id != targetId }
            .map { it.removeNode(targetId) }
        return copy(children = updatedChildren)
    }

    /** Helper to immutably update node properties or style. */
    fun updateNode(
        targetId: String,
        transform: (UiNode) -> UiNode,
    ): UiNode {
        if (id == targetId) return transform(this)
        return copy(children = children.map { it.updateNode(targetId, transform) })
    }
}
