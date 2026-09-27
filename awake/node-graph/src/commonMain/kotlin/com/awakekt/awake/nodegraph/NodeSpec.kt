/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * What one node type looks like: its ports, its settings and the words that describe it.
 *
 * The same spec is the editor's palette entry, the validator's rule and the catalogue entry a
 * tool reads, so the descriptions are part of the contract rather than decoration.
 */
@Serializable
data class NodeSpec(
    /** Stable id, referenced by [GraphNode.type]. Renaming it breaks saved graphs. */
    val type: String,
    val displayName: String,
    val category: String = "",
    val description: String = "",
    val inputs: List<PortSpec> = emptyList(),
    val outputs: List<PortSpec> = emptyList(),
    val config: List<ConfigFieldSpec> = emptyList(),
) {
    init {
        require(type.isNotBlank()) { "A node type needs a non-blank id." }
        requireUnique("input port", inputs.map { it.name })
        requireUnique("output port", outputs.map { it.name })
        requireUnique("config field", config.map { it.name })
    }

    private fun requireUnique(what: String, names: List<String>) {
        val duplicate = names.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicate == null) { "Node type '$type' declares $what '${duplicate?.key}' twice." }
    }
}

/** One port. */
@Serializable
data class PortSpec(
    val name: String,
    /** Opaque to this module; the [GraphKind] decides which types connect. */
    val type: String,
    val description: String = "",
    /** For an input, whether it accepts more than one edge. Outputs always fan out. */
    val multiple: Boolean = false,
)

/** One value set on a node rather than wired in. */
@Serializable
data class ConfigFieldSpec(
    val name: String,
    /** Opaque to this module, like [PortSpec.type]; the owning runtime reads the value. */
    val type: String,
    val description: String = "",
    /** Used when the node's config omits the field, or `null` when it is required. */
    val default: JsonElement? = null,
)
