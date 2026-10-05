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
 *
 * @property type Stable id, referenced by [GraphNode.type]. Renaming it breaks saved graphs.
 * @property displayName Human-readable label shown in editors and node palettes.
 * @property category Palette category or submenu under which this node is listed.
 * @property description Detailed explanation of what the node computes or does.
 * @property inputs List of input ports declared on this node type.
 * @property outputs List of output ports declared on this node type.
 * @property config List of configuration fields that can be statically authored on this node.
 */
@Serializable
data class NodeSpec(
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

/**
 * Specification for a single input or output port on a node.
 *
 * @property name Unique identifier of the port within its node.
 * @property type Opaque data type name evaluated by the owning [GraphKind].
 * @property description Explanatory text describing the port's role and expected values.
 * @property multiple For an input, whether it accepts multiple inbound connections; outputs always fan out.
 */
@Serializable
data class PortSpec(
    val name: String,
    val type: String,
    val description: String = "",
    val multiple: Boolean = false,
)

/**
 * Specification for a statically authored configuration field on a node.
 *
 * @property name Unique name of the configuration field.
 * @property type Opaque data type name of the configuration value.
 * @property description Explanatory text describing the configuration setting.
 * @property default Fallback value when the field is omitted in graph JSON, or `null` if required.
 */
@Serializable
data class ConfigFieldSpec(
    val name: String,
    val type: String,
    val description: String = "",
    val default: JsonElement? = null,
)
