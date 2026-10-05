/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

import kotlinx.serialization.Serializable

/**
 * Every node type one [kind] of graph may use.
 *
 * Registration is explicit rather than discovered by reflection: Kotlin/Native and wasm have too
 * little of it, and a palette built by scanning code shows everything a module happens to
 * contain. A type that was never registered cannot appear in a valid graph.
 *
 * @property kind The graph category and connection policy this registry governs.
 */
class NodeRegistry(val kind: GraphKind) {
    private val specsByType = LinkedHashMap<String, NodeSpec>()

    /** Registered specs, in registration order. */
    val specs: Collection<NodeSpec> get() = specsByType.values

    /** Adds [spec]. A type id may be registered once. */
    fun register(spec: NodeSpec): NodeRegistry {
        require(spec.type !in specsByType) {
            "Node type '${spec.type}' is already registered for '${kind.id}'."
        }
        specsByType[spec.type] = spec
        return this
    }

    /** The spec registered for [type], or `null`. */
    operator fun get(type: String): NodeSpec? = specsByType[type]

    /** Everything wrong with [graph] under this registry; empty when it is valid. */
    fun validate(graph: NodeGraph): List<GraphIssue> = graphIssues(graph)

    /** The whole vocabulary, for documentation and for tools that author graphs. */
    fun catalogue(): NodeCatalogue = NodeCatalogue(kind.id, specs.toList())
}

/**
 * A [NodeRegistry]'s contents, as data. Encode it with [NodeGraphJson.encodeCatalogue].
 *
 * @property kind Graph kind identifier for which this catalogue applies.
 * @property nodes List of node specifications available in this catalogue.
 */
@Serializable
data class NodeCatalogue(
    val kind: String,
    val nodes: List<NodeSpec>,
)
