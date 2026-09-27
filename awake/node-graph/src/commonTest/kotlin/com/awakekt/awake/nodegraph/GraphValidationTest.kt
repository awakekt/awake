/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GraphValidationTest {
    private val event = EventGraphs.valid

    @Test
    fun eachKindsValidGraphHasNoIssues() {
        assertEquals(emptyList(), EventGraphs.registry.validate(EventGraphs.valid))
        assertEquals(emptyList(), StateGraphs.registry.validate(StateGraphs.valid))
        assertEquals(emptyList(), FlowGraphs.registry.validate(FlowGraphs.valid))
    }

    @Test
    fun graphOfAnotherKindIsRejected() {
        assertIssues(setOf(GraphIssueCode.KIND_MISMATCH), event.copy(kind = "test.other"))
    }

    @Test
    fun unsupportedFormatVersionIsRejected() {
        assertIssues(setOf(GraphIssueCode.UNSUPPORTED_FORMAT_VERSION), event.copy(formatVersion = 2))
    }

    @Test
    fun duplicateNodeIdIsRejected() {
        val graph = event.copy(nodes = event.nodes + GraphNode("start", "event.start"))
        assertIssues(setOf(GraphIssueCode.DUPLICATE_NODE_ID), graph, nodeId = "start")
    }

    @Test
    fun unregisteredNodeTypeIsRejected() {
        val graph = event.copy(nodes = event.nodes + GraphNode("tick", "event.update"))
        assertIssues(setOf(GraphIssueCode.UNKNOWN_NODE_TYPE), graph, nodeId = "tick")
    }

    @Test
    fun undeclaredConfigFieldIsRejected() {
        val graph = event.withNode("ok") { it.copy(config = buildJsonObject { put("colour", "red") }) }
        assertIssues(setOf(GraphIssueCode.UNKNOWN_CONFIG_FIELD), graph, nodeId = "ok")
    }

    @Test
    fun edgeToMissingNodeIsRejected() {
        val graph = event.copy(edges = event.edges + GraphEdge("branch", "true", "ghost", "exec"))
        assertIssues(setOf(GraphIssueCode.DANGLING_EDGE), graph, nodeId = "branch")
    }

    @Test
    fun unknownPortIsRejectedOnEitherEnd() {
        val badOutput = event.copy(edges = event.edges + GraphEdge("branch", "maybe", "ok", "exec"))
        assertIssues(setOf(GraphIssueCode.UNKNOWN_PORT), badOutput, nodeId = "branch")
        // An output name used as an input is still unknown: direction is part of a port.
        val badInput = event.copy(edges = event.edges + GraphEdge("start", "then", "branch", "true"))
        assertIssues(setOf(GraphIssueCode.UNKNOWN_PORT), badInput, nodeId = "branch")
    }

    @Test
    fun incompatiblePortTypesAreRejected() {
        val graph = event.copy(
            edges = event.edges.map {
                if (it.toPort == "condition") GraphEdge("health", "value", "branch", "condition") else it
            },
        )
        assertIssues(setOf(GraphIssueCode.INCOMPATIBLE_PORTS), graph, nodeId = "branch")
    }

    @Test
    fun kindDecidesCompatibility() {
        // FlowGraphs.valid wires an f32 into a vec3 input; only its kind's rule allows that.
        val edge = FlowGraphs.valid.edges.single { it.fromNode == "half" }
        val strict = object : GraphKind {
            override val id = FlowGraphs.kind.id
            override val allowsCycles = false
        }
        val strictRegistry = NodeRegistry(strict).also { r -> FlowGraphs.registry.specs.forEach(r::register) }
        assertEquals(emptyList(), FlowGraphs.registry.validate(FlowGraphs.valid))
        assertEquals(
            listOf(GraphIssueCode.INCOMPATIBLE_PORTS),
            strictRegistry.validate(FlowGraphs.valid).map { it.code },
            "the same $edge must fail under the default rule",
        )
    }

    @Test
    fun singleEdgeInputRejectsASecondEdge() {
        val graph = event.copy(edges = event.edges + GraphEdge("limit", "value", "check", "a"))
        assertIssues(setOf(GraphIssueCode.INPUT_ALREADY_CONNECTED), graph, nodeId = "check")
    }

    @Test
    fun multipleEdgeInputAcceptsSeveral() {
        val graph = event.copy(
            nodes = event.nodes + GraphNode("again", "event.start"),
            edges = event.edges + GraphEdge("again", "then", "branch", "exec"),
        )
        assertEquals(emptyList(), EventGraphs.registry.validate(graph))
    }

    @Test
    fun cycleIsRejectedWhereTheKindForbidsIt() {
        val flow = FlowGraphs.valid
        val graph = flow.copy(
            nodes = flow.nodes + GraphNode("again", "math.multiply"),
            edges = listOf(
                GraphEdge("normal", "normal", "scale", "a"),
                GraphEdge("again", "result", "scale", "b"),
                GraphEdge("scale", "result", "again", "a"),
                GraphEdge("half", "value", "again", "b"),
                GraphEdge("scale", "result", "out", "color"),
            ),
        )
        assertIssues(setOf(GraphIssueCode.CYCLE), graph, registry = FlowGraphs.registry)
    }

    @Test
    fun cycleIsAllowedWhereTheKindAllowsIt() {
        // StateGraphs.valid loops patrol -> chase -> flee -> patrol.
        assertEquals(emptyList(), StateGraphs.registry.validate(StateGraphs.valid))
        val strict = object : GraphKind {
            override val id = StateGraphs.kind.id
            override val allowsCycles = false
        }
        val strictRegistry = NodeRegistry(strict).also { r -> StateGraphs.registry.specs.forEach(r::register) }
        assertEquals(listOf(GraphIssueCode.CYCLE), strictRegistry.validate(StateGraphs.valid).map { it.code })
    }

    @Test
    fun specRejectsDuplicatePortNames() {
        assertFailsWith<IllegalArgumentException> {
            NodeSpec("bad", "Bad", inputs = listOf(PortSpec("a", "x"), PortSpec("a", "y")))
        }
    }

    @Test
    fun registryRejectsDuplicateType() {
        assertFailsWith<IllegalArgumentException> {
            EventGraphs.registry.register(NodeSpec("event.start", "Again"))
        }
    }

    private fun assertIssues(
        expected: Set<GraphIssueCode>,
        graph: NodeGraph,
        nodeId: String? = null,
        registry: NodeRegistry = EventGraphs.registry,
    ) {
        val issues = registry.validate(graph)
        assertEquals(expected, issues.map { it.code }.toSet(), issues.joinToString { it.message })
        if (nodeId != null) assertEquals(listOf(nodeId), issues.map { it.nodeId }.distinct())
    }

    private fun NodeGraph.withNode(id: String, change: (GraphNode) -> GraphNode): NodeGraph =
        copy(nodes = nodes.map { if (it.id == id) change(it) else it })
}
