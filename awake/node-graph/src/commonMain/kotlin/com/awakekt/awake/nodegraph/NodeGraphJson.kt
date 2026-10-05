/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

import kotlinx.serialization.json.Json

/**
 * The JSON form of graphs and catalogues.
 *
 * Strict: an unknown key fails to decode instead of being dropped, so a file from a newer or
 * mistaken writer is refused rather than half-read. Pretty-printed, because graphs are reviewed as
 * diffs.
 */
object NodeGraphJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Serializes [graph] into a formatted JSON string.
     *
     * @param graph Node graph document to serialize.
     * @return Formatted JSON string representation.
     */
    fun encode(graph: NodeGraph): String = json.encodeToString(NodeGraph.serializer(), graph)

    /** Parses [text] without validating it; see [load]. */
    fun decode(text: String): NodeGraph = json.decodeFromString(NodeGraph.serializer(), text)

    /**
     * Parses and validates [text]. A graph with any issue is refused whole, never partially
     * loaded.
     *
     * @throws InvalidNodeGraphException when [NodeRegistry.validate] reports anything.
     */
    fun load(text: String, registry: NodeRegistry): NodeGraph {
        val graph = decode(text)
        val issues = registry.validate(graph)
        if (issues.isNotEmpty()) throw InvalidNodeGraphException(issues)
        return graph
    }

    /**
     * Serializes [catalogue] into a formatted JSON string.
     *
     * @param catalogue Node catalogue specification to serialize.
     * @return Formatted JSON string representation.
     */
    fun encodeCatalogue(catalogue: NodeCatalogue): String =
        json.encodeToString(NodeCatalogue.serializer(), catalogue)

    /**
     * Deserializes [text] into a [NodeCatalogue] model.
     *
     * @param text JSON string to parse.
     * @return Deserialized [NodeCatalogue] instance.
     */
    fun decodeCatalogue(text: String): NodeCatalogue =
        json.decodeFromString(NodeCatalogue.serializer(), text)
}

/**
 * Exception thrown when a node graph fails structural or semantic validation against a registry.
 *
 * @property issues List of validation findings describing the failure causes.
 */
class InvalidNodeGraphException(val issues: List<GraphIssue>) :
    IllegalArgumentException(
        issues.joinToString(separator = "\n", prefix = "Invalid node graph:\n") { "- ${it.message}" },
    )
