/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiJson
import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.ai.perception.PerceptionNodeKinds
import com.awakekt.awake.core.schema.PropertySchema
import com.awakekt.awake.core.schema.propertySchemaOf

/**
 * The node and state kinds definitions may use: a kind id mapped to typed parameters and a factory.
 *
 * Core's kinds (see [BuiltInNodeKinds] and [PerceptionNodeKinds]) are registered unless [includeBuiltIns] is false. A game or
 * plugin registers its own with [register], or with the shorthands [action], [condition] and
 * [state]. Registration is explicit and an id is registered once, so a palette shows exactly what a
 * project registered.
 *
 * Compile a definition once with [compileTree] or [compileStateMachine], which checks all of it and
 * reports every problem together, then build a running tree or machine per agent from the result.
 *
 * @param includeBuiltIns Whether to register Core's kinds.
 */
class AiRegistry(includeBuiltIns: Boolean = true) {
    private val nodes = LinkedHashMap<NodeKindId, NodeKind<*>>()
    private val states = LinkedHashMap<StateKindId, StateKind<*>>()

    init {
        if (includeBuiltIns) {
            BuiltInNodeKinds.registerInto(this)
            PerceptionNodeKinds.nodeKinds.forEach(::register)
        }
    }

    /** The registered node kinds, in registration order. */
    val nodeKinds: Collection<NodeKind<*>> get() = nodes.values

    /** The registered state kinds, in registration order. */
    val stateKinds: Collection<StateKind<*>> get() = states.values

    /** Adds [kind]. An id may be registered once. */
    fun register(kind: NodeKind<*>): AiRegistry {
        require(kind.id !in nodes) { "Node kind '${kind.id}' is already registered." }
        nodes[kind.id] = kind
        return this
    }

    /** Adds [kind]. An id may be registered once. */
    fun register(kind: StateKind<*>): AiRegistry {
        require(kind.id !in states) { "State kind '${kind.id}' is already registered." }
        states[kind.id] = kind
        return this
    }

    /** The node kind registered as [id], or `null`. */
    fun nodeKind(id: NodeKindId): NodeKind<*>? = nodes[id]

    /** The state kind registered as [id], or `null`. */
    fun stateKind(id: StateKindId): StateKind<*>? = states[id]

    /**
     * The parameters of the node kind [id] as a form an editor can show: each parameter's name,
     * type, default, range and hints.
     *
     * @throws IllegalArgumentException when [id] is not registered.
     */
    fun parameterSchema(id: NodeKindId): PropertySchema {
        val kind = requireNotNull(nodes[id]) { "Node kind '$id' is not registered." }
        return propertySchemaOf(kind.params, AiJson.data)
    }

    /**
     * The parameters of the state kind [id], as [parameterSchema] gives a node kind's.
     *
     * @throws IllegalArgumentException when [id] is not registered.
     */
    fun parameterSchema(id: StateKindId): PropertySchema {
        val kind = requireNotNull(states[id]) { "State kind '$id' is not registered." }
        return propertySchemaOf(kind.params, AiJson.data)
    }

    /**
     * Checks [definition] against this registry and prepares it for building. Every node is
     * built once on the way, so a definition that compiles builds.
     *
     * @throws AiDefinitionException with every problem found.
     */
    fun compileTree(definition: BehaviorTreeDefinition): CompiledBehaviorTree {
        val problems = mutableListOf<AiDefinitionProblem>()
        checkFormat(definition.formatVersion, BehaviorTreeDefinition.FORMAT_VERSION, problems)
        val root = NodeCompiler(this, problems, where = "").compile(definition.root, BehaviorTree.ROOT_PATH)
        if (root != null && problems.isEmpty()) tryInstantiate(root, "", problems)
        if (problems.isNotEmpty()) throw AiDefinitionException(problems)
        return CompiledBehaviorTree(definition, root!!)
    }

    /**
     * Checks [definition] against this registry and prepares it for building, as [compileTree]
     * does for a tree.
     *
     * @throws AiDefinitionException with every problem found.
     */
    fun compileStateMachine(definition: StateMachineDefinition): CompiledStateMachine =
        StateMachineCompiler(this, definition).compile()
}

internal fun checkFormat(found: Int, current: Int, problems: MutableList<AiDefinitionProblem>) {
    if (found != current) {
        problems += AiDefinitionProblem(
            AiProblemCode.UNSUPPORTED_FORMAT,
            "",
            "formatVersion $found is not supported here; expected $current. Read the document with AiDefinitionJson to migrate it.",
        )
    }
}

internal fun tryInstantiate(root: CompiledNode, where: String, problems: MutableList<AiDefinitionProblem>) {
    try {
        root.instantiate(seed = 0L)
    } catch (failure: NodeBuildFailure) {
        problems += AiDefinitionProblem(
            AiProblemCode.INVALID_PARAMS,
            "${where}node ${failure.node.path} (${failure.node.kind.id})",
            failure.message ?: "the node could not be built.",
            failure.node.id,
        )
    }
}
