/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.PortSpec
import kotlin.math.abs
import kotlin.math.pow

/**
 * Turns pointer events into [NodeGraphIntent]s, plus the preview state drawing needs: the wire
 * being dragged and the selection box.
 *
 * One state machine for the whole canvas rather than a handler per node, because a wire ends on a
 * different node from the one it started on, and a box selects nodes it never touched. Pointer
 * coordinates are canvas-local pixels.
 *
 * | Press on | Drag does | Release does |
 * |---|---|---|
 * | output port | draws a wire | connects to a compatible input under the pointer |
 * | connected input | picks that wire up | reconnects, or disconnects over empty canvas |
 * | node | moves the selection | on a click, selects just that node |
 * | empty canvas | pans | on a click, clears the selection |
 * | empty canvas, Shift or Ctrl/Cmd | draws a box | selects what the box touches; Shift adds |
 */
internal class CanvasGestures(val geometry: CanvasGeometry) {
    // Inputs, refreshed by the canvas every frame before events are dispatched.
    var graph: NodeGraph = NodeGraph(kind = "")
    lateinit var registry: NodeRegistry
    lateinit var viewport: NodeGraphViewport
    var selection: Set<String> = emptySet()
    var density: Float = 1f
    var onIntent: (NodeGraphIntent) -> Unit = {}

    var mode: Mode = Mode.Idle
        private set

    // Wire preview, read by drawing while mode is Wiring.
    var wireFromNode: GraphNode? = null
        private set
    var wireFromPort: PortSpec? = null
        private set
    var cursorX: Float = 0f
        private set
    var cursorY: Float = 0f
        private set

    /** The input under the dragged wire, and whether it would accept it. */
    var wireTarget: CanvasHit.Input? = null
        private set
    var wireTargetAccepted: Boolean = false
        private set

    /** Whether the current press has moved past the click slop. */
    val isDragging: Boolean get() = moved

    // Box preview, in canvas-local pixels, read by drawing while mode is Boxing.
    var boxStartX: Float = 0f
        private set
    var boxStartY: Float = 0f
        private set

    private var pressX = 0f
    private var pressY = 0f
    private var moved = false
    private var boxAdds = false
    private var detached: GraphEdge? = null
    private var movingIds: Set<String> = emptySet()
    private var clickedNode: String? = null

    enum class Mode { Idle, Panning, Boxing, Moving, Wiring }

    private val scale: Float get() = viewport.zoom * density

    fun press(x: Float, y: Float, shift: Boolean, accel: Boolean) {
        pressX = x
        pressY = y
        cursorX = x
        cursorY = y
        moved = false
        clickedNode = null
        val hit = geometry.hitTest(graph.nodes, registry, viewport.toCanvasX(x, density), viewport.toCanvasY(y, density))
        when (hit) {
            is CanvasHit.Output -> startWire(hit.node, hit.port, detaching = null)
            is CanvasHit.Input -> {
                val existing = graph.edges.lastOrNull { it.toNode == hit.node.id && it.toPort == hit.port.name }
                val source = existing?.let { edge -> graph.nodes.firstOrNull { it.id == edge.fromNode } }
                val sourcePort = source?.let { registry[it.type] }?.outputs?.firstOrNull { it.name == existing.fromPort }
                if (existing != null && source != null && sourcePort != null) {
                    startWire(source, sourcePort, detaching = existing)
                } else {
                    pressNode(hit.node, shift, accel)
                }
            }
            is CanvasHit.Node -> pressNode(hit.node, shift, accel)
            CanvasHit.Background -> {
                boxStartX = x
                boxStartY = y
                boxAdds = shift
                mode = if (shift || accel) Mode.Boxing else Mode.Panning
            }
        }
    }

    fun secondaryPress(x: Float, y: Float) {
        val canvasX = viewport.toCanvasX(x, density)
        val canvasY = viewport.toCanvasY(y, density)
        val nodeId = when (val hit = geometry.hitTest(graph.nodes, registry, canvasX, canvasY)) {
            is CanvasHit.Node -> hit.node.id
            is CanvasHit.Input -> hit.node.id
            is CanvasHit.Output -> hit.node.id
            CanvasHit.Background -> null
        }
        onIntent(NodeGraphIntent.ContextMenu(canvasX, canvasY, nodeId))
    }

    fun move(x: Float, y: Float) {
        if (mode == Mode.Idle) return
        if (!moved && abs(x - pressX) < DRAG_SLOP && abs(y - pressY) < DRAG_SLOP) return
        moved = true
        val dx = x - cursorX
        val dy = y - cursorY
        cursorX = x
        cursorY = y
        when (mode) {
            Mode.Panning -> viewport.panBy(dx, dy)
            Mode.Moving -> if (movingIds.isNotEmpty() && (dx != 0f || dy != 0f)) {
                onIntent(NodeGraphIntent.MoveNodes(movingIds, dx / scale, dy / scale))
            }
            Mode.Wiring -> updateWireTarget()
            Mode.Boxing, Mode.Idle -> Unit
        }
    }

    fun release(x: Float, y: Float) {
        if (mode != Mode.Idle) move(x, y)
        when (mode) {
            Mode.Wiring -> if (moved) finishWire()
            Mode.Boxing -> if (moved) finishBox()
            Mode.Panning -> if (!moved && selection.isNotEmpty()) onIntent(NodeGraphIntent.Select(emptySet()))
            Mode.Moving -> clickedNode?.let { if (!moved) onIntent(NodeGraphIntent.Select(setOf(it))) }
            Mode.Idle -> Unit
        }
        mode = Mode.Idle
        wireFromNode = null
        wireFromPort = null
        wireTarget = null
        detached = null
        movingIds = emptySet()
    }

    /** One wheel notch in or out, around the pointer. Negative [delta] zooms in. */
    fun wheel(x: Float, y: Float, delta: Float) {
        viewport.zoomBy(WHEEL_ZOOM_STEP.pow(-delta), x, y, density)
    }

    private fun startWire(node: GraphNode, port: PortSpec, detaching: GraphEdge?) {
        mode = Mode.Wiring
        wireFromNode = node
        wireFromPort = port
        detached = detaching
        updateWireTarget()
    }

    private fun pressNode(node: GraphNode, shift: Boolean, accel: Boolean) {
        val id = node.id
        val additive = shift || accel
        val next = when {
            additive && id in selection -> selection - id
            additive -> selection + id
            id in selection -> selection
            else -> setOf(id)
        }
        if (next != selection) onIntent(NodeGraphIntent.Select(next))
        // A plain click inside a multi-selection narrows it to this node -- but only on release,
        // so dragging the group still moves all of it.
        if (!additive && id in selection && selection.size > 1) clickedNode = id
        movingIds = if (id in next) next else emptySet()
        mode = Mode.Moving
    }

    private fun updateWireTarget() {
        val port = wireFromPort
        val target = geometry.inputAt(
            graph.nodes,
            registry,
            viewport.toCanvasX(cursorX, density),
            viewport.toCanvasY(cursorY, density),
        )
        wireTarget = target
        wireTargetAccepted = port != null && target != null && registry.kind.canConnect(port, target.port)
    }

    private fun finishWire() {
        val from = wireFromNode
        val fromPort = wireFromPort
        val target = wireTarget
        val lifted = detached
        if (from == null || fromPort == null) return
        if (target == null) {
            // Dropped on empty canvas: a lifted wire is removed, a new one simply not made.
            lifted?.let { onIntent(NodeGraphIntent.Disconnect(it)) }
            return
        }
        val edge = GraphEdge(from.id, fromPort.name, target.node.id, target.port.name)
        // Dropped back where it came from, or on an input that refuses it: the graph is unchanged.
        if (edge != lifted && wireTargetAccepted) graph.emitConnect(edge, target.port.multiple, lifted, onIntent)
    }

    private fun finishBox() {
        val box = CanvasBox(
            left = viewport.toCanvasX(minOf(boxStartX, cursorX), density),
            top = viewport.toCanvasY(minOf(boxStartY, cursorY), density),
            right = viewport.toCanvasX(maxOf(boxStartX, cursorX), density),
            bottom = viewport.toCanvasY(maxOf(boxStartY, cursorY), density),
        )
        val boxed = LinkedHashSet<String>()
        if (boxAdds) boxed += selection
        graph.nodes.forEach { node ->
            if (geometry.overlaps(node, registry[node.type], box)) boxed += node.id
        }
        if (boxed != selection) onIntent(NodeGraphIntent.Select(boxed))
    }

    private companion object {
        /** Pixels a press may wander before it counts as a drag rather than a click. */
        const val DRAG_SLOP = 3f

        /** Zoom factor per wheel notch. */
        const val WHEEL_ZOOM_STEP = 1.1f
    }
}

/**
 * The intents that wire [edge] in: lift [lifted] off its old input, free a single-edge input the
 * new wire replaces, then connect -- in that order, so a caller applying them one by one never
 * holds a graph with two wires in one single-edge input.
 */
private fun NodeGraph.emitConnect(
    edge: GraphEdge,
    targetTakesMany: Boolean,
    lifted: GraphEdge?,
    onIntent: (NodeGraphIntent) -> Unit,
) {
    lifted?.let { onIntent(NodeGraphIntent.Disconnect(it)) }
    if (!targetTakesMany) {
        edges.firstOrNull { it.toNode == edge.toNode && it.toPort == edge.toPort && it != lifted }
            ?.let { onIntent(NodeGraphIntent.Disconnect(it)) }
    }
    if (edge !in edges) onIntent(NodeGraphIntent.Connect(edge))
}
