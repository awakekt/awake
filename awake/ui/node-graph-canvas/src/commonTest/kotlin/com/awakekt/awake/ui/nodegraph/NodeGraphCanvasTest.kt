/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.ComposeTestSession
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.pointer.PointerModifiers
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphKind
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real canvas driven by pointer frames. With the default style at zoom 1 and density 1, the
 * source node's output port is at (220, 79) and the sink's inputs at (300, 79) and (300, 101).
 */
class NodeGraphCanvasTest {
    private val kind = object : GraphKind {
        override val id = "test"
        override val allowsCycles = false
    }
    private val registry = NodeRegistry(kind)
        .register(NodeSpec("source", "Source", outputs = listOf(PortSpec("out", "float"))))
        .register(NodeSpec("sink", "Sink", inputs = listOf(PortSpec("in", "float"), PortSpec("flag", "bool"))))

    private var graph = NodeGraph(
        kind = kind.id,
        nodes = listOf(GraphNode("a", "source", 40f, 40f), GraphNode("b", "sink", 300f, 40f)),
    )
    private var selection: Set<String> = emptySet()
    private val intents = ArrayList<NodeGraphIntent>()

    private fun canvas(
        viewport: NodeGraphViewport = NodeGraphViewport(),
        body: (
            context(Composer)
            (GraphNode, NodeSpec?) -> Unit
        )? = null,
    ): ComposeTestSession {
        val session = composeTestSession(WIDTH, HEIGHT) {
            NodeGraphCanvas(
                graph = graph,
                registry = registry,
                viewport = viewport,
                selection = selection,
                onIntent = { intent ->
                    intents += intent
                    graph = intent.applyTo(graph)
                    if (intent is NodeGraphIntent.Select) selection = intent.nodeIds
                },
                modifier = Modifier.fillMaxSize(),
                nodeContent = body,
            )
        }
        session.frame()
        return session
    }

    @Test
    fun draggingFromAnOutputToACompatibleInputConnectsThem() {
        canvas().drag(220, 79, 300, 79)
        assertEquals(listOf(GraphEdge("a", "out", "b", "in")), graph.edges)
    }

    @Test
    fun draggingToAnIncompatibleInputConnectsNothing() {
        canvas().drag(220, 79, 300, 101)
        assertEquals(emptyList(), graph.edges)
        assertEquals(emptyList(), intents)
    }

    @Test
    fun shiftDragOnEmptyCanvasBoxSelects() {
        canvas().drag(20, 20, 520, 200, PointerModifiers(isShiftPressed = true))
        assertEquals(setOf("a", "b"), selection)
    }

    @Test
    fun wheelZoomsAroundThePointer() {
        val viewport = NodeGraphViewport()
        val session = canvas(viewport)
        val before = viewport.toCanvasX(400f, 1f)
        session.frame(FrameInput(WIDTH, HEIGHT, pointerX = 400, pointerY = 300, scrollDeltaY = 1f))
        assertTrue(viewport.zoom > 1f, "wheel up zooms in")
        assertEquals(before, viewport.toCanvasX(400f, 1f), 1e-3f)
    }

    @Test
    fun zoomScalesNodeLayoutAndItIsHitWhereItIsDrawn() {
        val session = canvas(NodeGraphViewport(zoom = 2f)) { node, _ ->
            Box(Modifier.fillMaxWidth().height(20.dp).testTag("body-${node.id}"))
        }
        val body = session.frame().onNodeWithTag("body-b").getBoundsInRoot()
        assertEquals(360, body.width, "180 dp at zoom 2")
        assertEquals(600, body.left, "canvas x 300 at zoom 2")
        session.click(body.left + body.width / 2, body.top + body.height / 2)
        assertEquals(setOf("b"), selection)
    }

    @Test
    fun aControlInsideANodeKeepsItsOwnClick() {
        var clicks = 0
        val session = canvas { node, _ ->
            Box(Modifier.fillMaxWidth().height(20.dp).testTag("button-${node.id}").clickable { clicks++ })
        }
        val button = session.frame().onNodeWithTag("button-a").getBoundsInRoot()
        session.click(button.left + 10, button.top + 10)
        assertEquals(1, clicks)
        assertEquals(emptyList(), intents)
    }

    @Test
    fun draggingANodeMovesIt() {
        canvas().drag(100, 50, 160, 90)
        val moved = graph.nodes.first { it.id == "a" }
        assertEquals(100f, moved.x, 1e-3f)
        assertEquals(80f, moved.y, 1e-3f)
    }

    private fun ComposeTestSession.drag(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        modifiers: PointerModifiers = PointerModifiers.None,
    ) {
        frame(input(fromX, fromY, down = false, modifiers))
        frame(input(fromX, fromY, down = true, modifiers))
        for (step in 1..STEPS) {
            frame(input(fromX + (toX - fromX) * step / STEPS, fromY + (toY - fromY) * step / STEPS, down = true, modifiers))
        }
        frame(input(toX, toY, down = false, modifiers))
        frame()
    }

    private fun ComposeTestSession.click(x: Int, y: Int) {
        frame(input(x, y, down = false))
        frame(input(x, y, down = true))
        frame(input(x, y, down = false))
        frame()
    }

    private fun input(x: Int, y: Int, down: Boolean, modifiers: PointerModifiers = PointerModifiers.None) =
        FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerModifiers = modifiers)

    private companion object {
        const val WIDTH = 800
        const val HEIGHT = 600
        const val STEPS = 4
    }
}
