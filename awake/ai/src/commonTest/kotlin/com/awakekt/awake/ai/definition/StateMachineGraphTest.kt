/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.ai.fsm.AiState
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.NodeGraphJson
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StateMachineGraphTest {
    @Serializable
    data class GlowParams(val brightness: Float = 1f)

    private val registry = AiRegistry().state("lamp.glow", GlowParams.serializer(), "Glow") { name, _ -> AiState(name) }

    // A made-up lighthouse: dark until dusk, sweeping its beam, and any state goes dark on a storm.
    private val machine = StateMachineDefinition(
        id = "lighthouse",
        initial = "dark",
        blackboard = mapOf("sweeps" to BlackboardValue.IntValue(0)),
        states = listOf(
            StateDefinition("dark", layout = NodeLayout(0f, 100f)),
            StateDefinition(
                "sweep",
                kind = StateKindId("lamp.glow"),
                params = params("""{"brightness":0.8}"""),
                enter = listOf(node("ai.add", """{"key":"sweeps","amount":1}""")),
                exit = listOf(node("ai.emit", """{"event":"beam.off"}""")),
                tree = node("ai.wait", """{"seconds":2}"""),
                loopTree = false,
                layout = NodeLayout(200f, 100f),
            ),
        ),
        transitions = listOf(
            TransitionDefinition(from = "dark", to = "sweep", condition = node("ai.has", """{"key":"dusk"}""")),
            TransitionDefinition(from = "sweep", to = "dark", onTree = TreeOutcome.Success, layout = NodeLayout(100f, 160f)),
            TransitionDefinition(to = "dark", after = 0.5f, condition = node("ai.has", """{"key":"storm"}""")),
        ),
    )

    @Test
    fun aMachineRoundTripsThroughAGraphAndValidates() {
        val graph = machine.toNodeGraph()
        val nodeRegistry = registry.stateMachineNodeRegistry()
        assertEquals(emptyList(), nodeRegistry.validate(graph))
        val reloaded = NodeGraphJson.load(NodeGraphJson.encode(graph), nodeRegistry)
        assertEquals(machine, reloaded.toStateMachineDefinition())
        assertEquals(emptyList(), registry.validate(reloaded.toStateMachineDefinition()))
    }

    @Test
    fun theGraphShowsStatesTransitionsAndTheAnyStateTransition() {
        val graph = machine.toNodeGraph()
        assertEquals(
            listOf("machine", "state/dark", "state/sweep", "transition/0", "transition/1", "transition/2"),
            graph.nodes.map { it.id },
        )
        assertEquals("state/lamp.glow", graph.nodes.single { it.id == "state/sweep" }.type)
        // The storm transition leaves every state, so nothing is wired to its from input.
        assertTrue(graph.edges.none { it.toNode == "transition/2" && it.toPort == StateMachineGraphKind.FROM })
    }

    @Test
    fun theCatalogueListsStateKindsAndTransitionFields() {
        val catalogue = registry.stateMachineNodeRegistry().catalogue()
        val types = catalogue.nodes.map { it.type }
        assertTrue("state/ai.state" in types && "state/lamp.glow" in types, "$types")
        assertEquals(listOf("after", "onTree", "condition"), catalogue.nodes.single { it.type == StateMachineGraphKind.TRANSITION_TYPE }.config.map { it.name })
        assertEquals("Glow", catalogue.nodes.single { it.type == "state/lamp.glow" }.displayName)
    }

    @Test
    fun anEditorMayIdStatesAsItLikes() {
        val graph = machine.toNodeGraph()
        val renamed = graph.copy(
            nodes = graph.nodes.map { if (it.id == "state/dark") it.copy(id = "n7") else it },
            edges = graph.edges.map { edge ->
                edge.copy(fromNode = edge.fromNode.replace("state/dark", "n7"), toNode = edge.toNode.replace("state/dark", "n7"))
            },
        )
        assertEquals(machine, renamed.toStateMachineDefinition())
    }

    @Test
    fun aBrokenGraphListsWhatIsWrong() {
        val graph = machine.toNodeGraph()
        val broken = graph.copy(
            nodes = graph.nodes.map {
                when (it.id) {
                    "state/sweep" -> it.copy(config = params("""{"name":"sweep","tre":{}}"""))
                    else -> it
                }
            },
            edges = graph.edges.filterNot { it.fromNode == "machine" || (it.fromNode == "transition/0" && it.fromPort == StateMachineGraphKind.TO) },
        )
        val error = assertFailsWith<AiDefinitionException> { broken.toStateMachineDefinition() }
        val messages = error.problems.map { it.toString() }
        assertTrue(messages.any { it.startsWith("graph node 'state/sweep': its config does not read") && "tre" in it }, "$messages")
        assertTrue("graph node 'machine': its initial output is not wired to a state." in messages, "$messages")
        assertTrue("graph node 'transition/0': its to output is not wired to a state." in messages, "$messages")

        val twoMachines = graph.copy(nodes = graph.nodes + graph.nodes.first().copy(id = "machine2"))
        assertEquals(
            "a machine graph has one ai.machine node, but this one has 2.",
            assertFailsWith<AiDefinitionException> { twoMachines.toStateMachineDefinition() }.problems.first().message,
        )
        val badKind = graph.copy(
            nodes = graph.nodes.map { if (it.id == "state/dark") it.copy(type = "state/not a kind") else it },
            edges = graph.edges + GraphEdge("x", "y", "z", "w"),
        )
        assertTrue(
            assertFailsWith<AiDefinitionException> { badKind.toStateMachineDefinition() }.problems.any { "'not a kind' is not a state kind id." in it.message },
        )
    }
}
