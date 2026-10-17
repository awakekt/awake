/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.nodegraph.ConfigFieldSpec
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphKind
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * State machines as `awake:node-graph` documents, for a visual machine editor.
 *
 * A machine is three kinds of graph node:
 * - one [MACHINE_TYPE] node, carrying the machine's id and starting blackboard, whose [INITIAL] output
 *   is wired to the initial state;
 * - a state node per state, typed [STATE_TYPE_PREFIX] plus its kind id (`state/ai.state`), carrying its
 *   name, parameters, enter and exit nodes, tree and `loopTree` as config;
 * - a [TRANSITION_TYPE] node per transition, in order, carrying `after`, `onTree` and its condition.
 *   Its [FROM] input is wired from its state's [OUT] output, or left unwired for every state, and its
 *   [TO] output to its target's [IN] input.
 *
 * A state's own trees are kept in its config as behaviour tree definitions; an editor opens one with
 * [toNodeGraph] on [NodeDefinition] and puts it back with [toNodeDefinition].
 */
object StateMachineGraphKind : GraphKind {
    /** The type of the node carrying the machine's own fields. */
    const val MACHINE_TYPE: String = "ai.machine"

    /** The type of a transition node. */
    const val TRANSITION_TYPE: String = "ai.transition"

    /** What a state node's type starts with; its kind id follows. */
    const val STATE_TYPE_PREFIX: String = "state/"

    /** The port type that enters a state. */
    const val STATE_PORT_TYPE: String = "ai.state"

    /** The port type that leaves a state for a transition. */
    const val TRANSITION_PORT_TYPE: String = "ai.transition"

    /** A state's input: the machine's initial wire and transitions into it. */
    const val IN: String = "in"

    /** A state's output, wired to the transitions that leave it. */
    const val OUT: String = "out"

    /** A transition's input, from the state it leaves. */
    const val FROM: String = "from"

    /** A transition's output, to the state it enters. */
    const val TO: String = "to"

    /** The machine node's output, to the initial state. */
    const val INITIAL: String = "initial"

    override val id: String = "awake.ai.state-machine"
    override val allowsCycles: Boolean = true
}

/** The machine node's config. */
@Serializable
private data class MachineConfig(val id: String = "", val blackboard: Map<String, BlackboardValue> = emptyMap())

/** A state node's config. */
@Serializable
private data class StateConfig(
    val name: String,
    val params: JsonObject = JsonObject(emptyMap()),
    val enter: List<NodeDefinition> = emptyList(),
    val exit: List<NodeDefinition> = emptyList(),
    val tree: NodeDefinition? = null,
    val loopTree: Boolean = true,
)

/** A transition node's config. */
@Serializable
private data class TransitionConfig(
    val after: Float = 0f,
    val onTree: TreeOutcome? = null,
    val condition: NodeDefinition? = null,
)

private val graphJson = Json { explicitNulls = false }

private fun <T> T.toConfig(serializer: KSerializer<T>): JsonObject = graphJson.encodeToJsonElement(serializer, this) as JsonObject

/**
 * Every state kind in this registry, with the machine and transition nodes, as an `awake:node-graph`
 * registry for an editor's palette and for validating a machine graph.
 */
fun AiRegistry.stateMachineNodeRegistry(): NodeRegistry {
    val registry = NodeRegistry(StateMachineGraphKind)
    registry.register(
        NodeSpec(
            type = StateMachineGraphKind.MACHINE_TYPE,
            displayName = "State Machine",
            category = "Machine",
            description = "The machine itself: its id and starting blackboard. Wire initial to the state it starts in.",
            outputs = listOf(PortSpec(StateMachineGraphKind.INITIAL, StateMachineGraphKind.STATE_PORT_TYPE)),
            config = listOf(
                ConfigFieldSpec("id", "text", "A name for the machine."),
                ConfigFieldSpec("blackboard", "map", "The values a new agent's blackboard starts with."),
            ),
        ),
    )
    for (kind in stateKinds) {
        registry.register(
            NodeSpec(
                type = StateMachineGraphKind.STATE_TYPE_PREFIX + kind.id.value,
                displayName = kind.displayName,
                category = "State",
                description = kind.description,
                inputs = listOf(PortSpec(StateMachineGraphKind.IN, StateMachineGraphKind.STATE_PORT_TYPE, multiple = true)),
                outputs = listOf(PortSpec(StateMachineGraphKind.OUT, StateMachineGraphKind.TRANSITION_PORT_TYPE)),
                config = listOf(
                    ConfigFieldSpec("name", "text", "The state's name, unique in the machine."),
                    ConfigFieldSpec("params", "object", "The kind's parameters; see the registry's parameterSchema."),
                    ConfigFieldSpec("enter", "list", "Behaviour nodes ticked once on entering."),
                    ConfigFieldSpec("exit", "list", "Behaviour nodes ticked once on leaving."),
                    ConfigFieldSpec("tree", "object", "A behaviour tree ticked on every update."),
                    ConfigFieldSpec("loopTree", "boolean", "Whether the tree starts over after it finishes."),
                ),
            ),
        )
    }
    registry.register(
        NodeSpec(
            type = StateMachineGraphKind.TRANSITION_TYPE,
            displayName = "Transition",
            category = "Transition",
            description = "Leaves the state wired to from, or every other state when unwired, for the state wired to to.",
            inputs = listOf(PortSpec(StateMachineGraphKind.FROM, StateMachineGraphKind.TRANSITION_PORT_TYPE)),
            outputs = listOf(PortSpec(StateMachineGraphKind.TO, StateMachineGraphKind.STATE_PORT_TYPE)),
            config = listOf(
                ConfigFieldSpec("after", "float", "Seconds in the state before it can fire."),
                ConfigFieldSpec("onTree", "enum", "success, failure or finished: how the state's tree must have finished."),
                ConfigFieldSpec("condition", "object", "A behaviour node that must succeed."),
            ),
        ),
    )
    return registry
}

/**
 * This machine as a graph. States take the ids `state/<name>`, transitions `transition/<index>` and the
 * machine node `machine`; each keeps its layout as its position.
 */
fun StateMachineDefinition.toNodeGraph(): NodeGraph {
    val nodes = mutableListOf<GraphNode>()
    val edges = mutableListOf<GraphEdge>()
    fun stateId(name: String) = "state/$name"
    nodes += GraphNode(MACHINE_NODE, StateMachineGraphKind.MACHINE_TYPE, config = MachineConfig(id, blackboard).toConfig(MachineConfig.serializer()))
    edges += GraphEdge(MACHINE_NODE, StateMachineGraphKind.INITIAL, stateId(initial), StateMachineGraphKind.IN)
    for (state in states) {
        nodes += GraphNode(
            stateId(state.name),
            StateMachineGraphKind.STATE_TYPE_PREFIX + state.kind.value,
            state.layout?.x ?: 0f,
            state.layout?.y ?: 0f,
            StateConfig(state.name, state.params, state.enter, state.exit, state.tree, state.loopTree).toConfig(StateConfig.serializer()),
        )
    }
    transitions.forEachIndexed { index, transition ->
        val id = "transition/$index"
        nodes += GraphNode(
            id,
            StateMachineGraphKind.TRANSITION_TYPE,
            transition.layout?.x ?: 0f,
            transition.layout?.y ?: 0f,
            TransitionConfig(transition.after, transition.onTree, transition.condition).toConfig(TransitionConfig.serializer()),
        )
        transition.from?.let { edges += GraphEdge(stateId(it), StateMachineGraphKind.OUT, id, StateMachineGraphKind.FROM) }
        edges += GraphEdge(id, StateMachineGraphKind.TO, stateId(transition.to), StateMachineGraphKind.IN)
    }
    return NodeGraph(StateMachineGraphKind.id, nodes = nodes, edges = edges)
}

/**
 * A machine graph back as a [StateMachineDefinition]. States are named by their config, not their
 * graph ids, so an editor may id them as it likes; transitions keep the order of their nodes. A node
 * at (0, 0) has no layout, so a definition converted with [toNodeGraph] comes back equal. Validate the
 * graph with [stateMachineNodeRegistry] first for port and type problems.
 *
 * @throws AiDefinitionException with every problem: no machine node or several, an unwired initial
 *   state or transition target, a state type that is not a kind id, or config that does not read.
 */
fun NodeGraph.toStateMachineDefinition(): StateMachineDefinition = MachineGraphReader(this).read()

/** Reads a machine graph, collecting every problem before throwing. */
private class MachineGraphReader(private val graph: NodeGraph) {
    private val problems = mutableListOf<AiDefinitionProblem>()

    // A state's graph id to its name, which states are known by.
    private val names = HashMap<String, String>()

    fun read(): StateMachineDefinition {
        val machines = graph.nodes.filter { it.type == StateMachineGraphKind.MACHINE_TYPE }
        if (machines.size != 1) {
            problem(null, "a machine graph has one ${StateMachineGraphKind.MACHINE_TYPE} node, but this one has ${machines.size}.")
        }
        val states = graph.nodes.filter { it.type.startsWith(StateMachineGraphKind.STATE_TYPE_PREFIX) }.mapNotNull(::state)
        val machine = machines.singleOrNull()
        val config = machine?.decode(MachineConfig.serializer())
        val initial = machine?.let { initialOf(it) }
        val transitions = graph.nodes.filter { it.type == StateMachineGraphKind.TRANSITION_TYPE }.mapNotNull(::transition)
        if (problems.isNotEmpty()) throw AiDefinitionException(problems)
        return StateMachineDefinition(
            id = config!!.id,
            initial = initial!!,
            blackboard = config.blackboard,
            states = states,
            transitions = transitions,
        )
    }

    private fun problem(node: GraphNode?, message: String) {
        problems += AiDefinitionProblem(AiProblemCode.MALFORMED, node?.let { "graph node '${it.id}'" }.orEmpty(), message, node?.id)
    }

    private fun <T : Any> GraphNode.decode(serializer: KSerializer<T>): T? =
        try {
            graphJson.decodeFromJsonElement(serializer, config)
        } catch (e: IllegalArgumentException) {
            problem(this, "its config does not read: ${e.message?.lineSequence()?.first()}")
            null
        }

    private fun wired(fromNode: String?, fromPort: String, toNode: String?, toPort: String): GraphEdge? =
        graph.edges.firstOrNull {
            it.fromPort == fromPort && it.toPort == toPort &&
                (fromNode == null || it.fromNode == fromNode) && (toNode == null || it.toNode == toNode)
        }

    private fun initialOf(machine: GraphNode): String? {
        val initial = wired(machine.id, StateMachineGraphKind.INITIAL, null, StateMachineGraphKind.IN)?.toNode?.let(names::get)
        if (initial == null) problem(machine, "its initial output is not wired to a state.")
        return initial
    }

    private fun state(node: GraphNode): StateDefinition? {
        val kind = node.type.removePrefix(StateMachineGraphKind.STATE_TYPE_PREFIX)
        if (!isKindId(kind)) problem(node, "'$kind' is not a state kind id.")
        val config = if (isKindId(kind)) node.decode(StateConfig.serializer()) else null
        config?.let { names[node.id] = it.name }
        return config?.let {
            StateDefinition(it.name, StateKindId(kind), it.params, it.enter, it.exit, it.tree, it.loopTree, node.layout())
        }
    }

    private fun transition(node: GraphNode): TransitionDefinition? {
        val config = node.decode(TransitionConfig.serializer())
        val from = wired(null, StateMachineGraphKind.OUT, node.id, StateMachineGraphKind.FROM)?.fromNode
        val to = wired(node.id, StateMachineGraphKind.TO, null, StateMachineGraphKind.IN)?.toNode?.let(names::get)
        if (to == null) problem(node, "its to output is not wired to a state.")
        if (from != null && from !in names) problem(node, "its from input is wired to '$from', which is not a state.")
        return if (config == null || to == null) {
            null
        } else {
            TransitionDefinition(from?.let(names::get), to, config.after, config.onTree, config.condition, node.layout())
        }
    }
}

private const val MACHINE_NODE = "machine"

private fun GraphNode.layout(): NodeLayout? = if (x == 0f && y == 0f) null else NodeLayout(x, y)
