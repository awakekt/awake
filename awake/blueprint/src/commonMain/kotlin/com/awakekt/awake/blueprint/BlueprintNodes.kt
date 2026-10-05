/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.nodegraph.ConfigFieldSpec
import com.awakekt.awake.nodegraph.GraphKind
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec

/**
 * Blueprint graphs. Execution wires may loop back through a [LatentNode], so the generic cycle
 * check is off; [BlueprintCompiler] rejects data cycles and execution loops with no wait in them.
 */
object BlueprintGraphKind : GraphKind {
    override val id: String = "awake.logic.event-graph"
    override val allowsCycles: Boolean = true
}

/**
 * The node types blueprint graphs may use: the palette Studio shows, the rules a graph is validated
 * against, and the code that runs each node.
 *
 * Registering a node also makes each of its data inputs settable on the node, as a config field of
 * the same name and type, which is how an unwired input gets its value.
 */
class BlueprintNodes {
    /** The same vocabulary as specs, for validation and the Studio palette. */
    val registry: NodeRegistry = NodeRegistry(BlueprintGraphKind)
    private val nodesByType = HashMap<String, BlueprintNode>()

    /**
     * Registers a blueprint node type into this collection and the backing registry.
     *
     * @param node Blueprint node definition to register.
     * @return This registry instance for chained configuration.
     */
    fun register(node: BlueprintNode): BlueprintNodes {
        val spec = node.spec
        (spec.inputs + spec.outputs).forEach { port ->
            require(port.type == PortTypes.EXEC || port.type in PortTypes.data) {
                "'${spec.type}' port '${port.name}' has type '${port.type}', which blueprints do not know."
            }
        }
        registry.register(withSettableInputs(spec))
        nodesByType[spec.type] = node
        return this
    }

    /**
     * Retrieves the registered blueprint node matching the specified [type] identifier.
     *
     * @param type Unique node type identifier.
     * @return Registered blueprint node instance, or `null` if not registered.
     */
    operator fun get(type: String): BlueprintNode? = nodesByType[type]

    /** Companion factory providing default blueprint node registries. */
    companion object {
        /** A registry holding [CoreNodes]: events, flow, variables and math. */
        fun core(): BlueprintNodes = BlueprintNodes().apply { CoreNodes.all.forEach(::register) }
    }
}

private fun withSettableInputs(spec: NodeSpec): NodeSpec {
    val settable = spec.inputs
        .filter { input -> input.type != PortTypes.EXEC && spec.config.none { it.name == input.name } }
        .map { ConfigFieldSpec(it.name, it.type, it.description) }
    return if (settable.isEmpty()) spec else spec.copy(config = spec.config + settable)
}
