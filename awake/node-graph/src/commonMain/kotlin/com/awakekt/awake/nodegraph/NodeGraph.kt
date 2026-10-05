/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One graph document: blueprint logic, a state tree or a shader, told apart by [kind].
 *
 * The document only records structure. What a node does is up to the runtime or compiler that
 * owns the kind, and whether the structure is legal is [NodeRegistry.validate]'s job.
 *
 * @property kind The [GraphKind.id] this graph was authored for.
 * @property formatVersion Version of this document format, not of the kind's node vocabulary.
 * @property nodes List of nodes contained within this graph.
 * @property edges Ordered list of directed connections between ports in the graph.
 */
@Serializable
data class NodeGraph(
    val kind: String,
    val formatVersion: Int = FORMAT_VERSION,
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
) {
    /**
     * Constants and format specifications for [NodeGraph].
     */
    companion object {
        /** The only [formatVersion] this build reads and writes. */
        const val FORMAT_VERSION: Int = 1
    }
}

/** A placed node. [x] and [y] are its canvas position, in canvas units. */
@Serializable
data class GraphNode(
    /** Unique within its graph; edges and validation issues refer to nodes by it. */
    val id: String,
    /** The [NodeSpec.type] this node instantiates. */
    val type: String,
    val x: Float = 0f,
    val y: Float = 0f,
    /** Values set on the node rather than wired in, keyed by [ConfigFieldSpec.name]. */
    val config: JsonObject = JsonObject(emptyMap()),
)

/**
 * A wire from an output port of [fromNode] to an input port of [toNode].
 *
 * @property fromNode Source node identifier.
 * @property fromPort Output port name on the source node.
 * @property toNode Destination node identifier.
 * @property toPort Input port name on the destination node.
 */
@Serializable
data class GraphEdge(
    val fromNode: String,
    val fromPort: String,
    val toNode: String,
    val toPort: String,
)
