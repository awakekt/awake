/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.node

/**
 * One of a [LayoutNode]'s two child lists, with only the operations reconciliation needs.
 *
 * Not a `MutableList`: a caller holding one could restructure the tree between measure and place,
 * leaving placement running against sizes that no longer exist. These operations are the whole
 * vocabulary -- insert at a position, move an existing node, drop the tail.
 */
class NodeChildren internal constructor(private val owner: LayoutNode? = null) {
    private val items = mutableListOf<LayoutNode>()

    val size: Int get() = items.size

    operator fun get(index: Int): LayoutNode = items[index]

    val indices: IntRange get() = items.indices

    /** Covariant, so this doubles as the `List<Measurable>` a [MeasurePolicy] receives. */
    fun asList(): List<LayoutNode> = items

    fun add(node: LayoutNode) {
        node.parent = owner
        items += node
    }

    fun insertAt(index: Int, node: LayoutNode) {
        node.parent = owner
        items.add(index.coerceAtMost(items.size), node)
    }

    /** Moves an existing node into position -- how a keyed list survives reordering. */
    fun move(from: Int, to: Int) {
        if (from == to) return
        items.add(to, items.removeAt(from))
    }

    /** Drops everything from [index] onward: what the latest pass no longer declares. */
    fun truncateFrom(index: Int) {
        while (items.size > index) {
            val removed = items.removeAt(items.size - 1)
            removed.parent = null
            removed.dispose()
        }
    }

    fun clear() {
        while (items.isNotEmpty()) {
            val removed = items.removeAt(items.lastIndex)
            removed.parent = null
            removed.dispose()
        }
    }

    fun clearWithoutDispose() {
        for (i in items.indices) items[i].parent = null
        items.clear()
    }

    fun setFrom(nodes: List<LayoutNode>) {
        for (i in items.indices) items[i].parent = null
        items.clear()
        for (i in nodes.indices) {
            nodes[i].parent = owner
            items.add(nodes[i])
        }
    }
}
