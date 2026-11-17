/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.semantics

import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.node.SemanticsModifierNode

/**
 * What [SemanticsTreeBuilder] read to build its last tree (#1512): every layout node with semantics
 * it visited, in order, with its depth in the semantics tree, whether a merging ancestor absorbed it,
 * the configurations its modifiers declared, and the bounds it was reported at.
 *
 * A host that composes and measures nothing still asked for the whole tree each frame, which
 * allocated a configuration, a map and a node per element. [compare] walks the placed tree the way
 * the builder does, allocating nothing, and says whether the last tree still holds. The tree is a
 * pure function of what the trace records: the order and depth of a pre-order walk give its shape,
 * the declared configurations give each node's properties, and the bounds give its place.
 */
internal class SemanticsTrace {
    private val nodes = ArrayList<LayoutNode>()
    private val configs = ArrayList<SemanticsConfiguration>()
    private var ints = IntArray(INITIAL)
    private var size = 0
    private var cursor = 0
    private var configCursor = 0
    private var boundsMoved = false

    /** Whether anything has been recorded since the last [clear]. */
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun clear() {
        nodes.clear()
        configs.clear()
        size = 0
    }

    /** Records one visited node; [absorbed] nodes keep no bounds, since a merging ancestor reports them. */
    fun record(node: LayoutNode, depth: Int, absorbed: Boolean) {
        nodes += node
        val links = node.semanticsModifiers
        for (i in links.indices) configs += links[i].semanticsConfiguration
        ensure(size + FIELDS)
        ints[size] = depth
        ints[size + 1] = if (absorbed) 1 else 0
        ints[size + 2] = links.size
        ints[size + 3] = if (absorbed) 0 else node.absoluteX
        ints[size + 4] = if (absorbed) 0 else node.absoluteY
        ints[size + 5] = if (absorbed) 0 else node.width
        ints[size + 6] = if (absorbed) 0 else node.height
        size += FIELDS
    }

    /**
     * Walks the placed tree under [root] as the builder would and compares it with the trace: [SAME]
     * when the last tree still holds, [MOVED] when only reported bounds differ, so the last tree's
     * merged configurations still hold, and [CHANGED] otherwise.
     */
    fun compare(root: LayoutNode): Int {
        cursor = 0
        configCursor = 0
        boundsMoved = false
        val holds = !isEmpty && visit(root, depth = 0) && cursor == size
        return when {
            !holds -> CHANGED
            boundsMoved -> MOVED
            else -> SAME
        }
    }

    private fun visit(node: LayoutNode, depth: Int): Boolean {
        val links = node.semanticsModifiers
        return when {
            links.isEmpty() -> children(node) { visit(it, depth) }
            !matches(node, depth, absorbed = false) -> false
            !links.mergesDescendants() -> children(node) { visit(it, depth + 1) }
            else -> links.clearsDescendants() || absorb(node, depth + 1)
        }
    }

    private fun absorb(node: LayoutNode, depth: Int): Boolean = children(node) { child ->
        (child.semanticsModifiers.isEmpty() || matches(child, depth, absorbed = true)) && absorb(child, depth)
    }

    private fun matches(node: LayoutNode, depth: Int, absorbed: Boolean): Boolean {
        val at = cursor
        val links = node.semanticsModifiers
        val same = at + FIELDS <= size && nodes[at / FIELDS] === node && ints[at] == depth &&
            ints[at + 1] == (if (absorbed) 1 else 0) && ints[at + 2] == links.size && sameConfigs(links)
        if (same) {
            if (!absorbed) {
                boundsMoved = boundsMoved || ints[at + 3] != node.absoluteX || ints[at + 4] != node.absoluteY ||
                    ints[at + 5] != node.width || ints[at + 6] != node.height
            }
            configCursor += links.size
            cursor = at + FIELDS
        }
        return same
    }

    private fun sameConfigs(links: List<SemanticsModifierNode>): Boolean {
        for (i in links.indices) if (configs[configCursor + i] !== links[i].semanticsConfiguration) return false
        return true
    }

    private inline fun children(node: LayoutNode, visit: (LayoutNode) -> Boolean): Boolean {
        var holds = true
        var i = 0
        while (holds && i < node.children.size) {
            val child = node.children[i++]
            if (node.placesChild(child)) holds = visit(child)
        }
        i = 0
        while (holds && i < node.layers.size) holds = visit(node.layers[i++])
        return holds
    }

    private fun ensure(capacity: Int) {
        if (capacity > ints.size) ints = ints.copyOf(maxOf(capacity, ints.size * 2))
    }

    companion object {
        const val SAME = 0
        const val MOVED = 1
        const val CHANGED = 2
        private const val FIELDS = 7
        private const val INITIAL = 64 * FIELDS
    }
}

/** Whether any of these links merges its descendants into its node, as clearing ones do too. */
internal fun List<SemanticsModifierNode>.mergesDescendants(): Boolean {
    for (i in indices) {
        val config = this[i].semanticsConfiguration
        if (config.isMergingDescendants || config.isClearingDescendants) return true
    }
    return false
}

/** Whether any of these links clears its descendants rather than absorbing them. */
internal fun List<SemanticsModifierNode>.clearsDescendants(): Boolean {
    for (i in indices) if (this[i].semanticsConfiguration.isClearingDescendants) return true
    return false
}
