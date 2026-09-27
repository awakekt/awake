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
 */
@Serializable
data class NodeGraph(
    /** The [GraphKind.id] this graph was authored for. */
    val kind: String,
    /** Version of this document format, not of the kind's node vocabulary. */
    val formatVersion: Int = FORMAT_VERSION,
    val nodes: List<GraphNode> = emptyList(),
    /**
     * Order is significant: a port with several edges keeps them in list order, which is how
     * ordered children are stored.
     */
    val edges: List<GraphEdge> = emptyList(),
) {
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

/** A wire from an output port of [fromNode] to an input port of [toNode]. */
@Serializable
data class GraphEdge(
    val fromNode: String,
    val fromPort: String,
    val toNode: String,
    val toPort: String,
)
