/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.blueprint.BlueprintGraphKind
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A tiny graph builder: `node("id", "type", "key" to value)`, then `wire("a.out", "b.in")`. */
internal class GraphBuilder {
    private val nodes = ArrayList<GraphNode>()
    private val edges = ArrayList<GraphEdge>()

    fun node(id: String, type: String, vararg config: Pair<String, Any>) {
        val values = config.associate { (key, value) ->
            key to when (value) {
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
        nodes += GraphNode(id, type, config = JsonObject(values))
    }

    fun wire(from: String, to: String) {
        val (fromNode, fromPort) = from.split('.')
        val (toNode, toPort) = to.split('.')
        edges += GraphEdge(fromNode, fromPort, toNode, toPort)
    }

    fun build() = NodeGraph(kind = BlueprintGraphKind.id, nodes = nodes, edges = edges)
}

internal fun graph(block: GraphBuilder.() -> Unit): NodeGraph = GraphBuilder().apply(block).build()

internal const val STEP = 1f / 60f
