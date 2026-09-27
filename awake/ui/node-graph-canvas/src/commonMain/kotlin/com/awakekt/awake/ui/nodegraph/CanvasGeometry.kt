/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec

/**
 * Where a node's parts sit, in canvas units.
 *
 * Ports are laid out from fixed sizes -- header, then one row per port -- so their positions follow
 * from a node's position alone. That is what lets wires be drawn and ports be hit without waiting
 * for the node's content to be measured. Only the caller's body, below the ports, has a measured
 * height, recorded in [bodyHeights].
 */
internal class CanvasGeometry(
    val nodeWidth: Float,
    val headerHeight: Float,
    val portRowHeight: Float,
    val portHitRadius: Float,
) {
    /** Measured body heights by node id, in canvas units, written by layout each frame. */
    val bodyHeights = HashMap<String, Float>()

    /** A port's x: inputs sit on a node's left edge, outputs on its right. */
    fun portX(node: GraphNode, output: Boolean): Float = if (output) node.x + nodeWidth else node.x

    fun portY(node: GraphNode, index: Int): Float = node.y + headerHeight + (index + 0.5f) * portRowHeight

    fun portsHeight(spec: NodeSpec?): Float =
        if (spec == null) 0f else maxOf(spec.inputs.size, spec.outputs.size) * portRowHeight

    fun nodeHeight(node: GraphNode, spec: NodeSpec?): Float =
        headerHeight + portsHeight(spec) + (bodyHeights[node.id] ?: 0f)

    /** The topmost part of the graph at a canvas point. Later nodes are drawn, and so hit, first. */
    fun hitTest(nodes: List<GraphNode>, registry: NodeRegistry, x: Float, y: Float): CanvasHit {
        for (i in nodes.indices.reversed()) {
            val node = nodes[i]
            val spec = registry[node.type]
            val hit = portAt(node, spec, x, y) ?: if (contains(node, spec, x, y)) CanvasHit.Node(node) else null
            if (hit != null) return hit
        }
        return CanvasHit.Background
    }

    /** The input port at a canvas point, for dropping a wire. */
    fun inputAt(nodes: List<GraphNode>, registry: NodeRegistry, x: Float, y: Float): CanvasHit.Input? {
        for (i in nodes.indices.reversed()) {
            val node = nodes[i]
            val inputs = registry[node.type]?.inputs ?: continue
            val index = portIndexAt(node, inputs, portX(node, output = false), x, y)
            if (index >= 0) return CanvasHit.Input(node, inputs[index])
        }
        return null
    }

    /** Whether a node's box, header to body, overlaps [box]. */
    fun overlaps(node: GraphNode, spec: NodeSpec?, box: CanvasBox): Boolean {
        val horizontally = node.x <= box.right && node.x + nodeWidth >= box.left
        val vertically = node.y <= box.bottom && node.y + nodeHeight(node, spec) >= box.top
        return horizontally && vertically
    }

    private fun contains(node: GraphNode, spec: NodeSpec?, x: Float, y: Float): Boolean {
        val horizontally = x >= node.x && x <= node.x + nodeWidth
        val vertically = y >= node.y && y <= node.y + nodeHeight(node, spec)
        return horizontally && vertically
    }

    private fun portAt(node: GraphNode, spec: NodeSpec?, x: Float, y: Float): CanvasHit? {
        if (spec == null) return null
        val output = portIndexAt(node, spec.outputs, portX(node, output = true), x, y)
        val input = if (output >= 0) -1 else portIndexAt(node, spec.inputs, portX(node, output = false), x, y)
        return when {
            output >= 0 -> CanvasHit.Output(node, spec.outputs[output])
            input >= 0 -> CanvasHit.Input(node, spec.inputs[input])
            else -> null
        }
    }

    private fun portIndexAt(node: GraphNode, ports: List<PortSpec>, portX: Float, x: Float, y: Float): Int {
        val radiusSquared = portHitRadius * portHitRadius
        for (index in ports.indices) {
            val dx = x - portX
            val dy = y - portY(node, index)
            if (dx * dx + dy * dy <= radiusSquared) return index
        }
        return -1
    }
}

/** A rectangle in canvas units. */
internal class CanvasBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal sealed interface CanvasHit {
    data object Background : CanvasHit

    data class Node(val node: GraphNode) : CanvasHit

    data class Input(val node: GraphNode, val port: PortSpec) : CanvasHit

    data class Output(val node: GraphNode, val port: PortSpec) : CanvasHit
}
