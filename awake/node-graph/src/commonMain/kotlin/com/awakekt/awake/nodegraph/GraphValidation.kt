/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

/**
 * Error code categorizing why a node graph is invalid.
 */
enum class GraphIssueCode {
    /** The graph kind does not match the expected graph schema kind. */
    KIND_MISMATCH,

    /** The format version of the graph is not supported by this engine build. */
    UNSUPPORTED_FORMAT_VERSION,

    /** More than one node shares the same unique identifier. */
    DUPLICATE_NODE_ID,

    /** A node specifies a type not found in the active node registry. */
    UNKNOWN_NODE_TYPE,

    /** A node contains configuration fields not defined in its specification. */
    UNKNOWN_CONFIG_FIELD,

    /** An edge connects to a node ID that does not exist in the graph. */
    DANGLING_EDGE,

    /** An edge references a port name that does not exist on the node. */
    UNKNOWN_PORT,

    /** An edge connects incompatible source and destination port data types. */
    INCOMPATIBLE_PORTS,

    /** An input port that only accepts a single connection has multiple inbound edges. */
    INPUT_ALREADY_CONNECTED,

    /** The graph contains a directed cycle, but the graph kind requires an acyclic DAG. */
    CYCLE,
}

/**
 * Diagnostic finding describing a structural or semantic validation problem in a graph.
 *
 * @property code Error code identifying the issue type.
 * @property message Human-readable explanation of the validation failure.
 * @property nodeId The node an editor should highlight, or `null` for a whole-document issue.
 */
data class GraphIssue(
    val code: GraphIssueCode,
    val message: String,
    val nodeId: String? = null,
)

internal fun NodeRegistry.graphIssues(graph: NodeGraph): List<GraphIssue> = buildList {
    checkHeader(graph, kind)
    val nodesById = checkNodes(graph, this@graphIssues)
    val linkedEdges = checkEdges(graph, nodesById, this@graphIssues)
    if (!kind.allowsCycles) checkCycles(nodesById.keys, linkedEdges, kind)
}

private fun MutableList<GraphIssue>.checkHeader(graph: NodeGraph, kind: GraphKind) {
    if (graph.kind != kind.id) {
        add(GraphIssue(GraphIssueCode.KIND_MISMATCH, "Graph is '${graph.kind}', expected '${kind.id}'."))
    }
    if (graph.formatVersion != NodeGraph.FORMAT_VERSION) {
        add(
            GraphIssue(
                GraphIssueCode.UNSUPPORTED_FORMAT_VERSION,
                "formatVersion ${graph.formatVersion} is not supported; expected ${NodeGraph.FORMAT_VERSION}.",
            ),
        )
    }
}

/** Reports node-level problems and returns the first node for each id, in document order. */
private fun MutableList<GraphIssue>.checkNodes(graph: NodeGraph, registry: NodeRegistry): Map<String, GraphNode> {
    val nodesById = LinkedHashMap<String, GraphNode>()
    graph.nodes.forEach { node ->
        if (node.id in nodesById) {
            add(GraphIssue(GraphIssueCode.DUPLICATE_NODE_ID, "Node id '${node.id}' is used twice.", node.id))
            return@forEach
        }
        nodesById[node.id] = node
        val spec = registry[node.type]
        if (spec == null) {
            add(
                GraphIssue(
                    GraphIssueCode.UNKNOWN_NODE_TYPE,
                    "Node type '${node.type}' is not registered for '${registry.kind.id}'.",
                    node.id,
                ),
            )
            return@forEach
        }
        node.config.keys.filter { key -> spec.config.none { it.name == key } }.forEach { key ->
            add(GraphIssue(GraphIssueCode.UNKNOWN_CONFIG_FIELD, "'${node.type}' has no config field '$key'.", node.id))
        }
    }
    return nodesById
}

/** Reports edge problems and returns the edges whose two nodes exist, for the cycle check. */
private fun MutableList<GraphIssue>.checkEdges(
    graph: NodeGraph,
    nodesById: Map<String, GraphNode>,
    registry: NodeRegistry,
): List<GraphEdge> {
    val linked = ArrayList<GraphEdge>(graph.edges.size)
    val filledInputs = HashSet<Pair<String, String>>()
    graph.edges.forEach { edge ->
        val from = nodesById[edge.fromNode]
        val to = nodesById[edge.toNode]
        if (from == null || to == null) {
            val missing = if (from == null) edge.fromNode else edge.toNode
            add(
                GraphIssue(
                    GraphIssueCode.DANGLING_EDGE,
                    "An edge names node '$missing', which does not exist.",
                    (from ?: to)?.id,
                ),
            )
        } else {
            linked += edge
            checkPorts(edge, from, to, registry, filledInputs)
        }
    }
    return linked
}

private fun MutableList<GraphIssue>.checkPorts(
    edge: GraphEdge,
    from: GraphNode,
    to: GraphNode,
    registry: NodeRegistry,
    filledInputs: MutableSet<Pair<String, String>>,
) {
    val fromSpec = registry[from.type]
    val toSpec = registry[to.type]
    // An unregistered type was already reported; its ports cannot be checked.
    if (fromSpec == null || toSpec == null) return
    val output = fromSpec.outputs.firstOrNull { it.name == edge.fromPort }
    val input = toSpec.inputs.firstOrNull { it.name == edge.toPort }
    if (output == null) {
        add(GraphIssue(GraphIssueCode.UNKNOWN_PORT, "'${from.type}' has no output '${edge.fromPort}'.", from.id))
    }
    if (input == null) {
        add(GraphIssue(GraphIssueCode.UNKNOWN_PORT, "'${to.type}' has no input '${edge.toPort}'.", to.id))
    }
    if (output == null || input == null) return
    if (!registry.kind.canConnect(output, input)) {
        add(
            GraphIssue(
                GraphIssueCode.INCOMPATIBLE_PORTS,
                "Cannot connect ${output.type} '${edge.fromPort}' to ${input.type} '${edge.toPort}'.",
                to.id,
            ),
        )
    }
    if (!input.multiple && !filledInputs.add(to.id to input.name)) {
        add(
            GraphIssue(
                GraphIssueCode.INPUT_ALREADY_CONNECTED,
                "Input '${input.name}' accepts one edge and has more.",
                to.id,
            ),
        )
    }
}

/** Reports each node a back edge reaches, once. */
private fun MutableList<GraphIssue>.checkCycles(nodeIds: Set<String>, edges: List<GraphEdge>, kind: GraphKind) {
    val successors = HashMap<String, MutableList<String>>()
    edges.forEach { successors.getOrPut(it.fromNode) { ArrayList() } += it.toNode }
    val search = CycleSearch(successors)
    nodeIds.forEach(search::from)
    search.cycleNodes.forEach { node ->
        add(GraphIssue(GraphIssueCode.CYCLE, "Node '$node' is part of a cycle; '${kind.id}' graphs must be acyclic.", node))
    }
}

/** Iterative depth-first search, so a long chain cannot overflow a native or wasm stack. */
private class CycleSearch(private val successors: Map<String, List<String>>) {
    private val visiting = HashSet<String>()
    private val done = HashSet<String>()
    val cycleNodes = LinkedHashSet<String>()

    fun from(start: String) {
        if (start in done) return
        val stack = ArrayDeque<Pair<String, Iterator<String>>>()
        enter(start, stack)
        while (stack.isNotEmpty()) {
            val (node, next) = stack.last()
            if (!next.hasNext()) {
                visiting -= node
                done += node
                stack.removeLast()
                continue
            }
            val child = next.next()
            if (child in visiting) {
                cycleNodes += child
            } else if (child !in done) {
                enter(child, stack)
            }
        }
    }

    private fun enter(node: String, stack: ArrayDeque<Pair<String, Iterator<String>>>) {
        visiting += node
        stack.addLast(node to successors[node].orEmpty().iterator())
    }
}
