/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.NodeGraph

/**
 * An edit the user asked for. The canvas never changes the graph itself: the caller applies each
 * intent, which is where undo, validation and persistence live.
 */
sealed interface NodeGraphIntent {
    /**
     * Moves [nodeIds] by a canvas-unit delta. Sent on every drag step, so the move is live.
     *
     * @property nodeIds Set of node IDs being moved.
     * @property dx Horizontal offset delta in canvas units.
     * @property dy Vertical offset delta in canvas units.
     */
    data class MoveNodes(val nodeIds: Set<String>, val dx: Float, val dy: Float) : NodeGraphIntent

    /**
     * Connects two ports by adding a new [edge].
     *
     * @property edge The graph edge to create.
     */
    data class Connect(val edge: GraphEdge) : NodeGraphIntent

    /**
     * Disconnects two ports by removing an existing [edge].
     *
     * @property edge The graph edge to remove.
     */
    data class Disconnect(val edge: GraphEdge) : NodeGraphIntent

    /**
     * The whole new selection, not a change to the old one.
     *
     * @property nodeIds Full set of node IDs in the new selection.
     */
    data class Select(val nodeIds: Set<String>) : NodeGraphIntent

    /**
     * A secondary press at a canvas point, on [nodeId] or on empty canvas when it is `null`.
     *
     * @property x Horizontal position of the context-menu request in canvas coordinates.
     * @property y Vertical position of the context-menu request in canvas coordinates.
     * @property nodeId Target node under the cursor, or `null` if pressed on empty canvas.
     */
    data class ContextMenu(val x: Float, val y: Float, val nodeId: String?) : NodeGraphIntent
}

/**
 * [graph] with this intent's structural edit applied. Selection and context-menu intents do not
 * change the document and return [graph] unchanged.
 *
 * A convenience for callers without their own command stack; an editor with undo applies the
 * same edits through its commands instead.
 */
fun NodeGraphIntent.applyTo(graph: NodeGraph): NodeGraph = when (this) {
    is NodeGraphIntent.MoveNodes -> graph.copy(
        nodes = graph.nodes.map { node ->
            if (node.id in nodeIds) node.copy(x = node.x + dx, y = node.y + dy) else node
        },
    )
    is NodeGraphIntent.Connect -> graph.copy(edges = graph.edges + edge)
    is NodeGraphIntent.Disconnect -> graph.copy(edges = graph.edges - edge)
    is NodeGraphIntent.Select, is NodeGraphIntent.ContextMenu -> graph
}
