/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphIssue
import com.awakekt.awake.nodegraph.GraphIssueCode
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.InvalidNodeGraphException
import com.awakekt.awake.nodegraph.NodeGraph
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns a validated graph into a [BlueprintProgram].
 *
 * On top of the node-graph validator, a blueprint must have no data cycle, no execution loop without
 * a [LatentNode] in it, and one type per variable name.
 */
object BlueprintCompiler {
    /** @throws InvalidNodeGraphException listing every problem, when the graph is not a valid blueprint. */
    fun compile(graph: NodeGraph, nodes: BlueprintNodes): BlueprintProgram {
        val issues = nodes.registry.validate(graph)
        if (issues.isNotEmpty()) throw InvalidNodeGraphException(issues)
        val builder = ProgramBuilder(graph, nodes)
        val structural = builder.structuralIssues()
        if (structural.isNotEmpty()) throw InvalidNodeGraphException(structural)
        return builder.build()
    }
}

private class ProgramBuilder(private val graph: NodeGraph, registered: BlueprintNodes) {
    private val nodes: Array<BlueprintNode> = Array(graph.nodes.size) { registered[graph.nodes[it].type]!! }
    private val indexOf: Map<String, Int> = graph.nodes.withIndex().associate { (i, node) -> node.id to i }
    private val slots = SlotAllocator()
    private val variables = LinkedHashMap<String, BlueprintVariable>()
    private val variableIssues = ArrayList<GraphIssue>()

    init {
        graph.nodes.forEachIndexed { i, graphNode ->
            val node = nodes[i] as? VariableNode ?: return@forEachIndexed
            val name = (graphNode.config[CoreNodes.NAME] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val existing = variables[name]
            when {
                name.isEmpty() -> variableIssues += issue(GraphIssueCode.UNKNOWN_CONFIG_FIELD, "A variable node has no name.", graphNode)
                existing == null -> variables[name] = BlueprintVariable(name, node.variableType, slots.allocate(node.variableType))
                existing.type != node.variableType -> variableIssues += issue(
                    GraphIssueCode.INCOMPATIBLE_PORTS,
                    "Variable '$name' is used as ${existing.type} and ${node.variableType}.",
                    graphNode,
                )
            }
        }
    }

    fun structuralIssues(): List<GraphIssue> = variableIssues + cycleIssues(data = true) + cycleIssues(data = false)

    fun build(): BlueprintProgram {
        val outputSlots = Array(nodes.size) { i -> outputSlotsOf(i) }
        val table = NodeTable(
            inputSlots = Array(nodes.size) { IntArray(nodes[it].spec.inputs.size) },
            inputSources = Array(nodes.size) { IntArray(nodes[it].spec.inputs.size) { Slots.NONE } },
            outputSlots = outputSlots,
            execTargets = Array(nodes.size) { i -> execTargetsOf(i) },
            stateBase = IntArray(nodes.size) { i -> stateBaseOf(i) },
        )
        for (i in nodes.indices) resolveInputs(i, table)
        val events = HashMap<String, MutableList<Int>>()
        nodes.forEachIndexed { i, node -> if (node is EventNode) events.getOrPut(node.spec.type) { ArrayList() } += i }
        return BlueprintProgram(
            graph = graph,
            nodes = nodes,
            table = table,
            initial = slots.values(),
            variables = variables.values.toList(),
            events = events.mapValues { it.value.toIntArray() },
        )
    }

    private fun outputSlotsOf(i: Int): IntArray {
        val node = nodes[i]
        val variable = (node as? VariableNode)?.let { variables[nameOf(graph.nodes[i])] }
        return IntArray(node.spec.outputs.size) { o ->
            val port = node.spec.outputs[o]
            when {
                port.type == PortTypes.EXEC -> Slots.NONE
                variable != null && port.name == CoreNodes.VALUE -> variable.slot
                else -> slots.allocate(port.type)
            }
        }
    }

    private fun resolveInputs(i: Int, table: NodeTable) {
        val graphNode = graph.nodes[i]
        nodes[i].spec.inputs.forEachIndexed { p, port ->
            if (port.type == PortTypes.EXEC) {
                table.inputSlots[i][p] = Slots.NONE
                return@forEachIndexed
            }
            val edge = graph.edges.firstOrNull { it.toNode == graphNode.id && it.toPort == port.name }
            if (edge == null) {
                table.inputSlots[i][p] = slots.allocate(port.type, graphNode.config[port.name])
            } else {
                val source = indexOf.getValue(edge.fromNode)
                val output = nodes[source].spec.outputs.indexOfFirst { it.name == edge.fromPort }
                table.inputSlots[i][p] = table.outputSlots[source][output]
                table.inputSources[i][p] = source
            }
        }
    }

    private fun execTargetsOf(i: Int): Array<IntArray> {
        val id = graph.nodes[i].id
        return Array(nodes[i].spec.outputs.size) { o ->
            val port = nodes[i].spec.outputs[o]
            if (port.type != PortTypes.EXEC) {
                IntArray(0)
            } else {
                graph.edges.filter { it.fromNode == id && it.fromPort == port.name }
                    .map { indexOf.getValue(it.toNode) }
                    .toIntArray()
            }
        }
    }

    private fun stateBaseOf(i: Int): Int {
        val count = (nodes[i] as? LatentNode)?.floatState ?: 0
        if (count == 0) return Slots.NONE
        val first = slots.allocate(PortTypes.FLOAT)
        repeat(count - 1) { slots.allocate(PortTypes.FLOAT) }
        return Slots.index(first)
    }

    /**
     * Data wires must not form a cycle. Execution wires may, but only through a latent node: an
     * execution wire leaving a latent node is the one place a loop can wait.
     */
    private fun cycleIssues(data: Boolean): List<GraphIssue> {
        val successors = Array(nodes.size) { ArrayList<Int>() }
        graph.edges.forEach { edge ->
            val from = indexOf.getValue(edge.fromNode)
            if (edge.isData(from) == data && (data || nodes[from] !is LatentNode)) {
                successors[from] += indexOf.getValue(edge.toNode)
            }
        }
        val kind = if (data) "data" else "execution"
        return findCycleNodes(successors).map { i ->
            issue(GraphIssueCode.CYCLE, "Node '${graph.nodes[i].id}' is on a $kind loop with no wait in it.", graph.nodes[i])
        }
    }

    private fun GraphEdge.isData(from: Int): Boolean =
        nodes[from].spec.outputs.firstOrNull { it.name == fromPort }?.type != PortTypes.EXEC

    private fun nameOf(node: GraphNode): String = (node.config[CoreNodes.NAME] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun issue(code: GraphIssueCode, message: String, node: GraphNode) = GraphIssue(code, message, node.id)
}

/** Nodes a depth-first search reaches again while still on its path. */
private fun findCycleNodes(successors: Array<ArrayList<Int>>): Set<Int> {
    val state = IntArray(successors.size)
    val found = LinkedHashSet<Int>()
    fun visit(i: Int) {
        state[i] = VISITING
        for (next in successors[i]) {
            if (state[next] == VISITING) found += next else if (state[next] == UNSEEN) visit(next)
        }
        state[i] = DONE
    }
    for (i in successors.indices) if (state[i] == UNSEEN) visit(i)
    return found
}

private const val UNSEEN = 0
private const val VISITING = 1
private const val DONE = 2

/** Hands out slots and remembers each one's starting value. */
private class SlotAllocator {
    private val floats = ArrayList<Float>()
    private val ints = ArrayList<Int>()
    private val longs = ArrayList<Long>()
    private val refs = ArrayList<Any?>()

    fun allocate(type: String, value: JsonElement? = null): Int {
        val primitive = value as? JsonPrimitive
        return when (Slots.kindOf(type)) {
            Slots.FLOAT -> Slots.of(Slots.FLOAT, floats.size).also { floats += primitive?.floatOrNull ?: 0f }
            Slots.INT -> Slots.of(Slots.INT, ints.size).also { ints += intValue(type, primitive) }
            Slots.LONG -> Slots.of(Slots.LONG, longs.size).also { longs += NO_ENTITY }
            else -> Slots.of(Slots.REF, refs.size).also { refs += primitive?.contentOrNull }
        }
    }

    fun values() = SlotValues(floats.toFloatArray(), ints.toIntArray(), longs.toLongArray(), refs.toTypedArray())

    private fun intValue(type: String, primitive: JsonPrimitive?): Int = when (type) {
        PortTypes.BOOL -> if (primitive?.booleanOrNull == true) 1 else 0
        else -> primitive?.intOrNull ?: 0
    }
}

