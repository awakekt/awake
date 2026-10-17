/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.nodegraph.ConfigFieldSpec
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphKind
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec

/**
 * Behaviour trees as `awake:node-graph` documents, which is how a visual editor edits them: each
 * node is a graph node whose `children` output is wired, in order, to its children's `parent`
 * input. Node kinds become node specs, and their parameters config fields.
 */
object BehaviorTreeGraphKind : GraphKind {
    /** The port type both ports have. */
    const val PORT_TYPE: String = "ai.node"

    /** The input a node's parent wires to. */
    const val PARENT_PORT: String = "parent"

    /** The output wired to the children, in order. */
    const val CHILDREN_PORT: String = "children"

    override val id: String = "awake.ai.behavior-tree"
    override val allowsCycles: Boolean = false
}

/**
 * Every node kind in this registry as a `awake:node-graph` registry, for an editor's palette and
 * for validating a tree graph. Each kind's description and parameters (name, type, default) are
 * carried over.
 */
fun AiRegistry.behaviorTreeNodeRegistry(): NodeRegistry {
    val registry = NodeRegistry(BehaviorTreeGraphKind)
    for (kind in nodeKinds) {
        val schema = parameterSchema(kind.id)
        registry.register(
            NodeSpec(
                type = kind.id.value,
                displayName = kind.displayName,
                category = kind.category.name,
                description = kind.description,
                inputs = listOf(PortSpec(BehaviorTreeGraphKind.PARENT_PORT, BehaviorTreeGraphKind.PORT_TYPE)),
                outputs = if (kind.children.max == 0) {
                    emptyList()
                } else {
                    listOf(PortSpec(BehaviorTreeGraphKind.CHILDREN_PORT, BehaviorTreeGraphKind.PORT_TYPE))
                },
                config = schema.children.map {
                    ConfigFieldSpec(it.name, it.kind.name.lowercase(), it.hints.tooltip.orEmpty(), it.default.takeUnless { _ -> it.required })
                },
            ),
        )
    }
    return registry
}

/**
 * This tree as a graph. A node keeps its [NodeDefinition.id] as its graph id, or takes its path when
 * it has none, and its [NodeDefinition.layout] as its position.
 */
fun NodeDefinition.toNodeGraph(): NodeGraph {
    val nodes = mutableListOf<GraphNode>()
    val edges = mutableListOf<GraphEdge>()
    fun add(node: NodeDefinition, path: String): String {
        val id = node.id ?: path
        nodes += GraphNode(id, node.kind.value, node.layout?.x ?: 0f, node.layout?.y ?: 0f, node.params)
        node.children.forEachIndexed { index, child ->
            val childId = add(child, "$path.$index")
            edges += GraphEdge(id, BehaviorTreeGraphKind.CHILDREN_PORT, childId, BehaviorTreeGraphKind.PARENT_PORT)
        }
        return id
    }
    add(this, BehaviorTree.ROOT_PATH)
    return NodeGraph(BehaviorTreeGraphKind.id, nodes = nodes, edges = edges)
}

/**
 * A tree graph back as a root [NodeDefinition]. A graph id equal to the node's path is dropped, and
 * a node at (0, 0) has no layout, so a definition converted with [toNodeGraph] comes back equal.
 * Validate the graph with [behaviorTreeNodeRegistry] first for port and kind problems.
 *
 * @throws AiDefinitionException when the graph is not one tree: no root or several, or a node with
 *   two parents.
 */
fun NodeGraph.toNodeDefinition(): NodeDefinition {
    val byId = nodes.associateBy { it.id }
    val links = edges.filter { it.fromPort == BehaviorTreeGraphKind.CHILDREN_PORT && it.toPort == BehaviorTreeGraphKind.PARENT_PORT }
    val parents = links.groupBy { it.toNode }
    val problems = mutableListOf<AiDefinitionProblem>()
    parents.filterValues { it.size > 1 }.keys.forEach { problems += graphProblem(it, "it has more than one parent.") }
    links.filter { it.toNode !in byId || it.fromNode !in byId }.forEach {
        problems += graphProblem(it.fromNode, "it is wired to a missing node '${it.toNode}'.")
    }
    nodes.filterNot { isKindId(it.type) }.forEach {
        problems += AiDefinitionProblem(AiProblemCode.UNKNOWN_KIND, "graph node '${it.id}'", "'${it.type}' is not a node kind id.", it.id)
    }
    val roots = nodes.filter { it.id !in parents }
    if (roots.size != 1) {
        problems += AiDefinitionProblem(AiProblemCode.MALFORMED, "", "a tree has one root, but this graph has ${roots.size}: ${roots.map { it.id }}.")
    }
    val reached = mutableSetOf<String>()
    fun build(node: GraphNode, path: String): NodeDefinition {
        reached += node.id
        val children = links.filter { it.fromNode == node.id && it.toNode !in reached }.mapNotNull { byId[it.toNode] }
        return NodeDefinition(
            kind = NodeKindId(node.type),
            id = node.id.takeUnless { it == path },
            params = node.config,
            children = children.mapIndexed { index, child -> build(child, "$path.$index") },
            layout = if (node.x == 0f && node.y == 0f) null else NodeLayout(node.x, node.y),
        )
    }
    if (problems.isNotEmpty()) throw AiDefinitionException(problems)
    val root = build(roots.single(), BehaviorTree.ROOT_PATH)
    val unreached = nodes.map { it.id }.filter { it !in reached }
    if (unreached.isNotEmpty()) {
        throw AiDefinitionException(unreached.map { graphProblem(it, "it is not under the root, or it is in a loop.") })
    }
    return root
}

private fun graphProblem(nodeId: String, message: String) =
    AiDefinitionProblem(AiProblemCode.MALFORMED, "graph node '$nodeId'", message, nodeId)
