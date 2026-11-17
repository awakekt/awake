/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.node.LayoutNode

/**
 * Which layout node drew each primitive of one paint (#1512), for a host that names where a stale
 * frame came from. As the painter walks, each node marks where its output starts and where it ends,
 * handing the output back to its parent; a primitive belongs to the node of the last mark at or before it.
 * Only a paint that asks for it marks anything.
 */
internal class PaintOwners {
    private val positions = ArrayList<Int>()
    private val owners = ArrayList<LayoutNode?>()
    private val open = ArrayList<LayoutNode>()

    /** [node] starts drawing at output index [at]. */
    fun enter(at: Int, node: LayoutNode) {
        open.add(node)
        positions.add(at)
        owners.add(node)
    }

    /** The node drawing now is done at output index [at]: what follows is its parent's again. */
    fun exit(at: Int) {
        open.removeAt(open.lastIndex)
        positions.add(at)
        owners.add(open.lastOrNull())
    }

    /** The node that drew primitive [index], or null past the paint or before its first node. */
    fun ownerOf(index: Int): LayoutNode? {
        var found: LayoutNode? = null
        for (mark in positions.indices) {
            if (positions[mark] > index) break
            found = owners[mark]
        }
        return found
    }
}
