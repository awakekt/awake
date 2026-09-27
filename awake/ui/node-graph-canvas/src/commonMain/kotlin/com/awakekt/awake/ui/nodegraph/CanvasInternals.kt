/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.draw.ContentDrawScope
import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.compose.ui.graphics.drawscope.drawRetainedMesh
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.layout.Placeable
import com.awakekt.awake.compose.ui.layout.PlacementScope
import com.awakekt.awake.compose.ui.node.PointerInputNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.ColoredTriangleMesh
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.StrokeCap
import com.awakekt.awake.core.graphics2d.tessellateStrokeAa
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.PortSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Places each visible node's card at its screen position and records the height its body
 * measured, which is the one part of a node the geometry cannot know in advance.
 */
internal class CanvasMeasurePolicy(private val state: CanvasState) : MeasurePolicy {
    private val placeables = ArrayList<Placeable>()
    private var placeDensity = 1f

    private val place: PlacementScope.() -> Unit = {
        val viewport = state.gestures.viewport
        for (i in placeables.indices) {
            val node = state.visible[i]
            placeables[i].placeAt(
                viewport.toScreenX(node.x, placeDensity).roundToInt(),
                viewport.toScreenY(node.y, placeDensity).roundToInt(),
            )
        }
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        state.viewWidth = width
        state.viewHeight = height
        placeDensity = density
        val scale = state.gestures.viewport.zoom * density
        placeables.clear()
        val count = min(measurables.size, state.visible.size)
        for (i in 0 until count) {
            val placeable = measurables[i].measure(ChildConstraints)
            placeables += placeable
            recordBodyHeight(state.visible[i], placeable.height / scale)
        }
        return layout(width, height, place = place)
    }

    private fun recordBodyHeight(node: GraphNode, cardHeight: Float) {
        val geometry = state.geometry
        val body = cardHeight - geometry.headerHeight - geometry.portsHeight(state.gestures.registry[node.type])
        val known = geometry.bodyHeights[node.id]
        // Only on change: a boxed Float per node per frame is exactly the allocation to avoid.
        if (known == null || abs(known - body) > HEIGHT_EPSILON) geometry.bodyHeights[node.id] = body
    }

    private companion object {
        val ChildConstraints = Constraints.of(0, Constraints.MaxDimension, 0, Constraints.MaxDimension)
        const val HEIGHT_EPSILON = 0.01f
    }
}

/**
 * Draws what lies outside the node cards: the background and wires underneath them, then ports,
 * the wire being dragged and the selection box on top.
 */
internal class CanvasPainter(private val state: CanvasState) {
    private val wires = WireRenderer(state.style)
    private val ends = WireEnds()
    private val nodesById = HashMap<String, GraphNode>()
    private var indexedGraph: NodeGraph? = null

    val draw: ContentDrawScope.() -> Unit = {
        drawRect(color = state.style.background)
        drawWires()
        drawContent()
        drawPorts()
        drawWirePreview()
        drawSelectionBox()
    }

    private fun DrawScope.drawWires() {
        val gestures = state.gestures
        val graph = gestures.graph
        indexNodes(graph)
        wires.begin(graph.edges.size, gestures.viewport.zoom * density)
        val edges = graph.edges
        for (i in edges.indices) {
            val edge = edges[i]
            if (resolveEnds(edge) && wires.isOnScreen(ends, width, height)) wires.draw(this, edge, ends)
        }
    }

    /** Fills [ends] for [edge] in screen pixels; false when either end or port no longer exists. */
    private fun DrawScope.resolveEnds(edge: GraphEdge): Boolean {
        val registry = state.gestures.registry
        val from = nodesById[edge.fromNode]
        val to = nodesById[edge.toNode]
        val outputs = from?.let { registry[it.type] }?.outputs.orEmpty()
        val inputs = to?.let { registry[it.type] }?.inputs.orEmpty()
        val out = indexOf(outputs, edge.fromPort)
        val input = indexOf(inputs, edge.toPort)
        // A port index is found only when its node and spec exist, so this covers every missing piece.
        val portsFound = out >= 0 && input >= 0
        if (!portsFound || from == null || to == null) return false
        val geometry = state.geometry
        ends.x0 = screenX(geometry.portX(from, output = true))
        ends.y0 = screenY(geometry.portY(from, out))
        ends.x1 = screenX(geometry.portX(to, output = false))
        ends.y1 = screenY(geometry.portY(to, input))
        ends.color = state.style.portColor(outputs[out].type)
        return true
    }

    private fun DrawScope.drawPorts() {
        val registry = state.gestures.registry
        val radius = state.style.portRadius.value * state.gestures.viewport.zoom * density
        val visible = state.visible
        for (i in visible.indices) {
            val node = visible[i]
            val spec = registry[node.type] ?: continue
            val inputX = screenX(state.geometry.portX(node, output = false))
            val outputX = screenX(state.geometry.portX(node, output = true))
            for (p in spec.inputs.indices) dot(inputX, screenY(state.geometry.portY(node, p)), radius, spec.inputs[p])
            for (p in spec.outputs.indices) dot(outputX, screenY(state.geometry.portY(node, p)), radius, spec.outputs[p])
        }
    }

    private fun DrawScope.drawWirePreview() {
        val gestures = state.gestures
        val node = gestures.wireFromNode
        val port = gestures.wireFromPort
        val outputs = node?.let { gestures.registry[it.type] }?.outputs
        if (gestures.mode != CanvasGestures.Mode.Wiring) return
        if (node == null || port == null || outputs == null) return
        val rejected = gestures.wireTarget != null && !gestures.wireTargetAccepted
        ends.x0 = screenX(state.geometry.portX(node, output = true))
        ends.y0 = screenY(state.geometry.portY(node, indexOf(outputs, port.name)))
        ends.x1 = gestures.cursorX
        ends.y1 = gestures.cursorY
        ends.color = if (rejected) state.style.rejectedWireColor else state.style.portColor(port.type)
        wires.drawTransient(this, ends)
    }

    private fun DrawScope.drawSelectionBox() {
        val gestures = state.gestures
        if (gestures.mode != CanvasGestures.Mode.Boxing || !gestures.isDragging) return
        val left = min(gestures.boxStartX, gestures.cursorX)
        val top = min(gestures.boxStartY, gestures.cursorY)
        val width = abs(gestures.cursorX - gestures.boxStartX)
        val height = abs(gestures.cursorY - gestures.boxStartY)
        val edge = density
        val color = state.style.selectionBoxBorderColor
        drawRect(left, top, width, height, state.style.selectionBoxColor)
        drawRect(left, top, width, edge, color)
        drawRect(left, top + height - edge, width, edge, color)
        drawRect(left, top, edge, height, color)
        drawRect(left + width - edge, top, edge, height, color)
    }

    private fun DrawScope.dot(x: Float, y: Float, radius: Float, port: PortSpec) {
        drawRoundedRect(x - radius, y - radius, radius * 2, radius * 2, state.style.portColor(port.type), radius)
    }

    private fun DrawScope.screenX(canvasX: Float): Float = state.gestures.viewport.toScreenX(canvasX, density)

    private fun DrawScope.screenY(canvasY: Float): Float = state.gestures.viewport.toScreenY(canvasY, density)

    private fun indexNodes(graph: NodeGraph) {
        if (graph === indexedGraph) return
        indexedGraph = graph
        nodesById.clear()
        graph.nodes.forEach { nodesById[it.id] = it }
    }

    private fun indexOf(ports: List<PortSpec>, name: String): Int {
        for (i in ports.indices) if (ports[i].name == name) return i
        return -1
    }
}

/** A wire's two ends in screen pixels and its colour; one instance, refilled per wire. */
internal class WireEnds {
    var x0 = 0f
    var y0 = 0f
    var x1 = 0f
    var y1 = 0f
    var color: Color = Color.Black
}

/**
 * Wires as triangles kept from the last frame their ends, width or colour changed, so an idle
 * canvas tessellates nothing. Panning and zooming move every end and rebuild.
 */
internal class WireRenderer(private val style: NodeGraphCanvasStyle) {
    private class Wire {
        var x0 = 0f
        var y0 = 0f
        var x1 = 0f
        var y1 = 0f
        var scale = 0f
        var color: Color = Color.Black
        var mesh: ColoredTriangleMesh? = null
        var retentionKey: Any = Any()
    }

    private val cache = HashMap<GraphEdge, Wire>()
    private var stroke = DrawStroke()
    private var scale = -1f

    /** Starts a frame at [scale], dropping wires for edges that are gone once they pile up. */
    fun begin(edgeCount: Int, scale: Float) {
        if (cache.size > edgeCount * 2 + STALE_WIRE_SLACK) cache.clear()
        if (scale == this.scale) return
        this.scale = scale
        // DrawStroke.width reaches the tessellator as pixels, so the zoom is applied here.
        stroke = DrawStroke(width = (style.wireWidth.value * scale).dp, cap = StrokeCap.Round)
    }

    fun isOnScreen(ends: WireEnds, width: Int, height: Int): Boolean {
        val reach = reach(ends)
        val horizontally = max(ends.x0, ends.x1) + reach >= 0f && min(ends.x0, ends.x1) - reach <= width
        val vertically = max(ends.y0, ends.y1) >= 0f && min(ends.y0, ends.y1) <= height
        return horizontally && vertically
    }

    fun draw(scope: DrawScope, edge: GraphEdge, ends: WireEnds) {
        val wire = cache.getOrPut(edge) { Wire() }
        val mesh = wire.mesh
        if (mesh != null && wire.matches(ends)) return scope.drawRetainedMesh(mesh, wire.retentionKey)
        val built = path(ends).tessellateStrokeAa(stroke, ends.color)
        wire.x0 = ends.x0
        wire.y0 = ends.y0
        wire.x1 = ends.x1
        wire.y1 = ends.y1
        wire.scale = scale
        wire.color = ends.color
        wire.mesh = built
        wire.retentionKey = Any()
        scope.drawRetainedMesh(built, wire.retentionKey)
    }

    /** A wire that changes every frame, like the one being dragged: stroked, not retained. */
    fun drawTransient(scope: DrawScope, ends: WireEnds) {
        scope.drawStrokedPath(path(ends), stroke, ends.color)
    }

    private fun Wire.matches(ends: WireEnds): Boolean =
        x0 == ends.x0 && y0 == ends.y0 && x1 == ends.x1 && y1 == ends.y1 && scale == this@WireRenderer.scale &&
            color == ends.color

    private fun path(ends: WireEnds): DrawPath {
        val reach = reach(ends)
        return DrawPath.build {
            moveTo(ends.x0, ends.y0)
            cubicTo(ends.x0 + reach, ends.y0, ends.x1 - reach, ends.y1, ends.x1, ends.y1)
        }
    }

    /** How far the curve bows out horizontally: more for a longer wire, never flat. */
    private fun reach(ends: WireEnds): Float = max(MIN_CONTROL_REACH * scale, abs(ends.x1 - ends.x0) / 2f)

    private companion object {
        const val MIN_CONTROL_REACH = 40f
        const val STALE_WIRE_SLACK = 16
    }
}

/** Feeds the canvas's pointer events to [CanvasGestures]; anything a node's own controls took is left alone. */
internal class CanvasPointerElement(private val gestures: CanvasGestures) : ModifierNodeElement<CanvasPointerNode>() {
    override fun create(): CanvasPointerNode = CanvasPointerNode(gestures)

    override fun update(node: CanvasPointerNode) {
        node.gestures = gestures
    }
}

internal class CanvasPointerNode(var gestures: CanvasGestures) :
    Modifier.Node(),
    PointerInputNode {
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        // Main pass: a node body's own controls see the event first and keep what they consume.
        if (pass != PointerEventPass.Main || event.pointerId != MOUSE_POINTER) return
        when (event.type) {
            PointerEventType.Press, PointerEventType.SecondaryPress, PointerEventType.Wheel -> start(event)
            PointerEventType.Move, PointerEventType.Release -> track(event)
            // LongPress fires during any press held half a second, including a slow drag.
            else -> Unit
        }
    }

    /** Events that begin something; skipped when a control inside a node already took them. */
    private fun start(event: PointerEvent) {
        if (event.isConsumed) return
        val x = event.x.toFloat()
        val y = event.y.toFloat()
        when (event.type) {
            PointerEventType.Press -> gestures.press(x, y, event.modifiers.isShiftPressed, event.modifiers.isAccelPressed)
            PointerEventType.SecondaryPress -> gestures.secondaryPress(x, y)
            else -> {
                if (event.scrollDelta == 0f) return
                // Wheel up is a positive delta and zooms in.
                gestures.wheel(x, y, -event.scrollDelta)
                event.consumeScrollDelta(event.scrollDelta)
            }
        }
        event.consume()
    }

    /** Events of a press this canvas holds. */
    private fun track(event: PointerEvent) {
        if (!event.isCaptureHolder) return
        if (event.type == PointerEventType.Move) {
            gestures.move(event.x.toFloat(), event.y.toFloat())
        } else {
            gestures.release(event.x.toFloat(), event.y.toFloat())
        }
        event.consume()
    }

    private companion object {
        const val MOUSE_POINTER = 0L
    }
}
