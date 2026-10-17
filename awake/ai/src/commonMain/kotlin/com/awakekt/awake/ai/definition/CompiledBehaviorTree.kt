/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BehaviorNode
import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.btree.BehaviorTreeNode
import com.awakekt.awake.ai.btree.Blackboard
import com.awakekt.awake.ai.btree.TracedNode
import com.awakekt.awake.ai.btree.TreeTrace

/**
 * A [BehaviorTreeDefinition] that [AiRegistry.compileTree] has checked, with its parameters read.
 * Build one running tree per agent from it; each keeps its own state.
 *
 * @property definition The definition it was compiled from.
 */
class CompiledBehaviorTree internal constructor(
    val definition: BehaviorTreeDefinition,
    private val root: CompiledNode,
) {
    /** How many nodes each tree has. */
    val nodeCount: Int get() = root.size

    /**
     * A new running tree. [seed] seeds its random nodes, so agents given different seeds decide
     * differently and one given the same seed decides the same way again.
     */
    fun newTree(seed: Long = 0L): BehaviorTree = root.instantiate(seed)

    /** A new blackboard holding the definition's starting values. */
    fun newBlackboard(): Blackboard = Blackboard().apply { setValues(definition.blackboard) }

    /** A component with a [newTree] and a [newBlackboard], ready to add to an entity. */
    fun newComponent(seed: Long = 0L): BehaviorTreeComponent = BehaviorTreeComponent(newTree(seed), newBlackboard())
}

/** One checked node: its kind, its read parameters and its children. */
internal class CompiledNode(
    val kind: NodeKind<*>,
    val params: Any,
    val path: String,
    val id: String?,
    val children: List<CompiledNode>,
) {
    val size: Int = 1 + children.sumOf { it.size }
}

/** A factory refused its parameters while building [node]. */
internal class NodeBuildFailure(val node: CompiledNode, cause: IllegalArgumentException) : IllegalArgumentException(cause.message, cause)

@Suppress("UNCHECKED_CAST") // params was decoded with this kind's own serializer.
private fun NodeKind<*>.create(params: Any, scope: NodeBuildScope): BehaviorNode =
    (this as NodeKind<Any>).factory.create(params, scope)

/** Builds a running tree, wrapping each node so its status is recorded. */
internal fun CompiledNode.instantiate(seed: Long): BehaviorTree {
    val trace = TreeTrace(size)
    val nodes = arrayOfNulls<BehaviorTreeNode>(size)
    var next = 0
    fun build(node: CompiledNode): BehaviorNode {
        val slot = next++
        val children = node.children.map(::build)
        val inner = try {
            node.kind.create(node.params, NodeBuildScope(node.path, children, mixSeed(seed, slot.toLong())))
        } catch (e: IllegalArgumentException) {
            throw NodeBuildFailure(node, e)
        }
        nodes[slot] = BehaviorTreeNode(node.path, inner, node.id, node.kind.id)
        return TracedNode(inner, slot, trace)
    }
    val root = build(this)
    return BehaviorTree(root, nodes.map { it!! }, trace)
}

/** Checks the nodes of one tree, collecting problems; [where] prefixes each location. */
internal class NodeCompiler(
    private val registry: AiRegistry,
    private val problems: MutableList<AiDefinitionProblem>,
    private val where: String,
) {
    private val ids = mutableSetOf<String>()

    fun compile(definition: NodeDefinition, path: String): CompiledNode? {
        val location = "${where}node $path (${definition.kind})"
        fun problem(code: AiProblemCode, message: String) {
            problems += AiDefinitionProblem(code, location, message, definition.id)
        }
        definition.id?.let { if (!ids.add(it)) problem(AiProblemCode.DUPLICATE_NODE_ID, "id '$it' is already used in this tree.") }
        val children = definition.children.mapIndexed { index, child -> compile(child, "$path.$index") }
        val kind = registry.nodeKind(definition.kind)
        if (kind == null) {
            val known = registry.nodeKinds.map { it.id.value }
            problem(AiProblemCode.UNKNOWN_KIND, "unknown node kind '${definition.kind}'${didYouMean(definition.kind.value, known)}.")
        } else if (!kind.children.accepts(definition.children.size)) {
            problem(
                AiProblemCode.WRONG_CHILD_COUNT,
                "${kind.id} takes ${kind.children.describe()}, but has ${definition.children.size}.",
            )
        }
        val params = kind?.let { decodeParams(it.params, definition.params, it.id) }
        params?.exceptionOrNull()?.let { problem(AiProblemCode.INVALID_PARAMS, it.message.orEmpty()) }
        val value = params?.getOrNull()
        val complete = kind != null && value != null && children.none { it == null } && kind.children.accepts(children.size)
        return if (complete) CompiledNode(kind, value, path, definition.id, children.map { it!! }) else null
    }
}
