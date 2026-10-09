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

    /** The number of nodes in this list. */
    val size: Int get() = items.size

    /** Returns the node at [index]. */
    operator fun get(index: Int): LayoutNode = items[index]

    /** The valid positions in this list. */
    val indices: IntRange get() = items.indices

    /** Covariant, so this doubles as the `List<Measurable>` a [MeasurePolicy] receives. */
    fun asList(): List<LayoutNode> = items

    /** Appends [node] and makes this list's owner its parent. */
    fun add(node: LayoutNode) {
        node.parent = owner
        items += node
    }

    /** Inserts [node] at [index], or at the end if [index] is past it, and makes the owner its parent. */
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

    /** Removes every node, detaching each from its parent and disposing it. */
    fun clear() {
        while (items.isNotEmpty()) {
            val removed = items.removeAt(items.lastIndex)
            removed.parent = null
            removed.dispose()
        }
    }

    /** Removes every node and detaches it from its parent, without disposing it. */
    fun clearWithoutDispose() {
        for (i in items.indices) items[i].parent = null
        items.clear()
    }

    /** Replaces the contents with [nodes], parented to the owner; the previous nodes are detached, not disposed. */
    fun setFrom(nodes: List<LayoutNode>) {
        for (i in items.indices) items[i].parent = null
        items.clear()
        for (i in nodes.indices) {
            nodes[i].parent = owner
            items.add(nodes[i])
        }
    }
}
