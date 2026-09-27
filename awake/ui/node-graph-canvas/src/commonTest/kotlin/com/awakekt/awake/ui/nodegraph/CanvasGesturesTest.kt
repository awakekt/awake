/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphKind
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Screen and canvas coincide here (zoom 1, no pan, density 1) unless a test says otherwise, so the
 * coordinates below read straight off the layout: nodes 100 wide, a 20 header, 20 per port row.
 * `source`'s output sits at (100, 30); `sink`'s inputs at (200, 30) and (200, 50).
 */
class CanvasGesturesTest {
    private val kind = object : GraphKind {
        override val id = "test"
        override val allowsCycles = false
    }
    private val registry = NodeRegistry(kind)
        .register(NodeSpec("source", "Source", outputs = listOf(PortSpec("out", FLOAT))))
        .register(NodeSpec("sink", "Sink", inputs = listOf(PortSpec("in", FLOAT), PortSpec("flag", BOOL))))

    private val intents = ArrayList<NodeGraphIntent>()
    private val viewport = NodeGraphViewport()
    private val gestures = CanvasGestures(CanvasGeometry(100f, 20f, 20f, 6f)).also {
        it.registry = registry
        it.viewport = viewport
        it.onIntent = intents::add
    }

    private fun graph(vararg edges: GraphEdge) = NodeGraph(
        kind = kind.id,
        nodes = listOf(
            GraphNode("a", "source", 0f, 0f),
            GraphNode("b", "sink", 200f, 0f),
            GraphNode("c", "sink", 200f, 100f),
        ),
        edges = edges.toList(),
    ).also { gestures.graph = it }

    private fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float, shift: Boolean = false, accel: Boolean = false) {
        gestures.press(fromX, fromY, shift, accel)
        gestures.move((fromX + toX) / 2, (fromY + toY) / 2)
        gestures.move(toX, toY)
        gestures.release(toX, toY)
    }

    private fun click(x: Float, y: Float, shift: Boolean = false) {
        gestures.press(x, y, shift, accel = false)
        gestures.release(x, y)
    }

    @Test
    fun wireFromOutputToCompatibleInputConnects() {
        graph()
        drag(100f, 30f, 200f, 30f)
        assertEquals(listOf<NodeGraphIntent>(NodeGraphIntent.Connect(GraphEdge("a", "out", "b", "in"))), intents)
    }

    @Test
    fun wireToIncompatibleInputIsRejectedWhileHoveringAndOnDrop() {
        graph()
        gestures.press(100f, 30f, shift = false, accel = false)
        gestures.move(200f, 50f)
        assertEquals("flag", gestures.wireTarget?.port?.name)
        assertFalse(gestures.wireTargetAccepted)
        gestures.release(200f, 50f)
        assertEquals(emptyList(), intents)
    }

    @Test
    fun wireDroppedOnEmptyCanvasDoesNothing() {
        graph()
        drag(100f, 30f, 150f, 200f)
        assertEquals(emptyList(), intents)
    }

    @Test
    fun liftingAWireOffItsInputAndDroppingItOnEmptyCanvasDisconnects() {
        val edge = GraphEdge("a", "out", "b", "in")
        graph(edge)
        drag(200f, 30f, 150f, 200f)
        assertEquals(listOf<NodeGraphIntent>(NodeGraphIntent.Disconnect(edge)), intents)
    }

    @Test
    fun liftingAWireOntoAnotherInputReconnects() {
        val edge = GraphEdge("a", "out", "b", "in")
        graph(edge)
        drag(200f, 30f, 200f, 130f)
        assertEquals(
            listOf(NodeGraphIntent.Disconnect(edge), NodeGraphIntent.Connect(GraphEdge("a", "out", "c", "in"))),
            intents,
        )
    }

    @Test
    fun liftingAWireAndDroppingItBackChangesNothing() {
        graph(GraphEdge("a", "out", "b", "in"))
        drag(200f, 30f, 200f, 31f)
        assertEquals(emptyList(), intents)
    }

    @Test
    fun connectingToAnOccupiedSingleInputReplacesItsWire() {
        val old = GraphEdge("x", "out", "b", "in")
        gestures.graph = graph().let { it.copy(nodes = it.nodes + GraphNode("x", "source", 0f, 300f), edges = listOf(old)) }
        drag(100f, 30f, 200f, 30f)
        assertEquals(
            listOf(NodeGraphIntent.Disconnect(old), NodeGraphIntent.Connect(GraphEdge("a", "out", "b", "in"))),
            intents,
        )
    }

    @Test
    fun clickSelectsShiftClickTogglesAndClickingEmptyCanvasClears() {
        graph()
        click(250f, 10f)
        assertEquals(NodeGraphIntent.Select(setOf("b")), intents.last())

        gestures.selection = setOf("b")
        click(250f, 110f, shift = true)
        assertEquals(NodeGraphIntent.Select(setOf("b", "c")), intents.last())

        gestures.selection = setOf("b", "c")
        click(250f, 110f, shift = true)
        assertEquals(NodeGraphIntent.Select(setOf("b")), intents.last())

        gestures.selection = setOf("b")
        click(500f, 500f)
        assertEquals(NodeGraphIntent.Select(emptySet()), intents.last())
    }

    @Test
    fun draggingANodeMovesItInCanvasUnits() {
        graph()
        viewport.zoomBy(2f, 0f, 0f, density = 1f)
        // At zoom 2, node b's header is at screen (500, 20); a 40 px drag is 20 canvas units.
        gestures.selection = setOf("b")
        drag(500f, 20f, 540f, 20f)
        val moves = intents.filterIsInstance<NodeGraphIntent.MoveNodes>()
        assertEquals(setOf("b"), moves.map { it.nodeIds }.toSet().single())
        assertEquals(20f, moves.sumOf { it.dx.toDouble() }.toFloat(), 1e-4f)
        assertEquals(0f, moves.sumOf { it.dy.toDouble() }.toFloat(), 1e-4f)
    }

    @Test
    fun draggingInsideAMultiSelectionMovesAllOfIt() {
        graph()
        gestures.selection = setOf("b", "c")
        drag(250f, 10f, 260f, 10f)
        assertTrue(intents.isNotEmpty())
        intents.forEach { assertEquals(setOf("b", "c"), (it as NodeGraphIntent.MoveNodes).nodeIds) }
    }

    @Test
    fun clickingInsideAMultiSelectionNarrowsItOnRelease() {
        graph()
        gestures.selection = setOf("b", "c")
        click(250f, 10f)
        assertEquals(listOf<NodeGraphIntent>(NodeGraphIntent.Select(setOf("b"))), intents)
    }

    @Test
    fun aPressThatWandersLessThanTheSlopIsStillAClick() {
        graph()
        gestures.press(250f, 10f, shift = false, accel = false)
        gestures.move(251f, 11f)
        gestures.release(251f, 11f)
        assertEquals(listOf<NodeGraphIntent>(NodeGraphIntent.Select(setOf("b"))), intents)
    }

    @Test
    fun boxSelectReplacesWithCtrlAndAddsWithShift() {
        graph()
        gestures.selection = setOf("a")
        // Covers b (200..300, 0..60) but not c (starts at y = 100).
        drag(180f, -10f, 320f, 70f, accel = true)
        assertEquals(NodeGraphIntent.Select(setOf("b")), intents.last())

        drag(180f, -10f, 320f, 70f, shift = true)
        assertEquals(NodeGraphIntent.Select(setOf("a", "b")), intents.last())
    }

    @Test
    fun draggingEmptyCanvasPans() {
        graph()
        drag(500f, 500f, 530f, 480f)
        assertEquals(30f, viewport.panX)
        assertEquals(-20f, viewport.panY)
        assertEquals(emptyList(), intents)
    }

    @Test
    fun wheelZoomKeepsThePointUnderTheCursorFixed() {
        graph()
        val beforeX = viewport.toCanvasX(420f, 1f)
        val beforeY = viewport.toCanvasY(170f, 1f)
        gestures.wheel(420f, 170f, -3f)
        assertTrue(viewport.zoom > 1f)
        assertEquals(beforeX, viewport.toCanvasX(420f, 1f), 1e-3f)
        assertEquals(beforeY, viewport.toCanvasY(170f, 1f), 1e-3f)
    }

    @Test
    fun secondaryPressReportsTheCanvasPointAndNode() {
        graph()
        viewport.panBy(10f, 0f)
        gestures.secondaryPress(260f, 10f)
        gestures.secondaryPress(900f, 900f)
        assertEquals(
            listOf<NodeGraphIntent>(NodeGraphIntent.ContextMenu(250f, 10f, "b"), NodeGraphIntent.ContextMenu(890f, 900f, null)),
            intents,
        )
    }

    private companion object {
        const val FLOAT = "float"
        const val BOOL = "bool"
    }
}
