/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeGraphJsonTest {
    @Test
    fun roundTripIsLosslessForEveryKind() {
        listOf(EventGraphs.valid, StateGraphs.valid, FlowGraphs.valid).forEach { graph ->
            assertEquals(graph, NodeGraphJson.decode(NodeGraphJson.encode(graph)))
        }
    }

    @Test
    fun formatVersionIsWrittenEvenAtItsDefault() {
        // A file without it would be read as whatever version the reader defaults to.
        assertTrue("\"formatVersion\": 1" in NodeGraphJson.encode(EventGraphs.valid))
    }

    @Test
    fun edgeOrderSurvivesSoOrderedChildrenDo() {
        val decoded = NodeGraphJson.decode(NodeGraphJson.encode(StateGraphs.valid))
        val children = decoded.edges.filter { it.fromNode == "root" }.map { it.toNode }
        assertEquals(listOf("patrol", "chase", "flee"), children)
    }

    @Test
    fun loadReturnsAValidGraph() {
        val text = NodeGraphJson.encode(EventGraphs.valid)
        assertEquals(EventGraphs.valid, NodeGraphJson.load(text, EventGraphs.registry))
    }

    @Test
    fun loadRefusesAGraphNamingAnUnregisteredType() {
        val graph = EventGraphs.valid.copy(nodes = EventGraphs.valid.nodes + GraphNode("tick", "event.update"))
        val failure = assertFailsWith<InvalidNodeGraphException> {
            NodeGraphJson.load(NodeGraphJson.encode(graph), EventGraphs.registry)
        }
        assertEquals(listOf(GraphIssueCode.UNKNOWN_NODE_TYPE), failure.issues.map { it.code })
    }

    @Test
    fun unknownKeyFailsToDecodeInsteadOfBeingDropped() {
        val text = NodeGraphJson.encode(EventGraphs.valid).replaceFirst("{", "{\n    \"comment\": \"x\",")
        assertFailsWith<SerializationException> { NodeGraphJson.decode(text) }
    }

    @Test
    fun catalogueRoundTripsWithItsDescriptions() {
        val catalogue = EventGraphs.registry.catalogue()
        val text = NodeGraphJson.encodeCatalogue(catalogue)
        assertEquals(catalogue, NodeGraphJson.decodeCatalogue(text))
        assertEquals(EventGraphs.kind.id, catalogue.kind)
        assertTrue("Runs one of two paths." in text)
        assertEquals(EventGraphs.registry.specs.map { it.type }, catalogue.nodes.map { it.type })
    }
}
