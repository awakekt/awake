/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphKind
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Bytes and milliseconds per frame for a 200-node, 199-wire graph at rest, on the same
 * reconcile + layout + paint loop `ComposeFrameProbe` measures, so the numbers can be set beside
 * that probe's.
 *
 * Two views, because culling decides what a frame costs: every node on screen at zoom 0.25 is the
 * worst case, and zoom 1 over the same graph is what an editor usually shows.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class NodeGraphCanvasFrameProbe {
    private val kind = object : GraphKind {
        override val id = "probe"
        override val allowsCycles = false
    }
    private val registry = NodeRegistry(kind).register(
        NodeSpec("pass", "Pass", inputs = listOf(PortSpec("in", "float")), outputs = listOf(PortSpec("out", "float"))),
    )
    private val graph = NodeGraph(
        kind = kind.id,
        nodes = List(NODES) { i -> GraphNode("n$i", "pass", (i % COLUMNS) * 220f, (i / COLUMNS) * 110f) },
        edges = List(NODES - 1) { i -> GraphEdge("n$i", "out", "n${i + 1}", "in") },
    )
    private val painter = Painter()

    @Test
    fun everyNodeOnScreen() = probe("all 200 visible, zoom 0.25", NodeGraphViewport(zoom = 0.25f), ALL_VISIBLE_CEILING)

    @Test
    fun editorView() = probe("editor view, zoom 1", NodeGraphViewport(zoom = 1f), EDITOR_VIEW_CEILING)

    private fun probe(label: String, viewport: NodeGraphViewport, ceiling: Long) {
        val root = LayoutNode(ColumnMeasurePolicy())
        var primitives = 0
        repeat(WARMUP_FRAMES) { primitives = frame(root, viewport) }

        val bytesBefore = allocatedBytes()
        val start = TimeSource.Monotonic.markNow()
        repeat(MEASURED_FRAMES) { frame(root, viewport) }
        val elapsed = start.elapsedNow()
        val perFrame = (allocatedBytes() - bytesBefore) / MEASURED_FRAMES
        val msPerFrame = elapsed.inWholeMicroseconds / 1000.0 / MEASURED_FRAMES

        println(
            "NodeGraphCanvasFrameProbe [$label]: $perFrame B/frame, ${"%.3f".format(msPerFrame)} ms/frame, " +
                "$primitives primitives; total ${elapsed.inWholeMilliseconds} ms over $MEASURED_FRAMES frames",
        )
        assertTrue(perFrame < ceiling, "$perFrame B/frame exceeds the $ceiling B ratchet for $label")
    }

    private fun frame(root: LayoutNode, viewport: NodeGraphViewport): Int {
        composeInto(root) {
            NodeGraphCanvas(graph, registry, viewport, emptySet(), {}, Modifier.fillMaxSize())
        }
        root.layoutTree(Constraints.fixed(FRAME_WIDTH, FRAME_HEIGHT))
        return painter.paint(root).size
    }

    private fun allocatedBytes(): Long =
        (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val NODES = 200
        const val COLUMNS = 20
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 800
        const val WARMUP_FRAMES = 500
        const val MEASURED_FRAMES = 300

        // Ratchets, not targets: about 10% above the measurement, lowered as fixes land. Measured
        // 2026-09-27 on an M-series Mac: 343 KB (all visible) and 451 KB (editor view) per frame.
        //
        // Where it goes, measured by phase: nearly all of it is the engine's per-node work -- each
        // Text re-measured and one primitive per glyph, every frame. The canvas's own share is
        // small: idle wires redraw retained meshes (about 3 KB for 60 wires, from about 48 KB when
        // they were stroked paths), and below labelMinZoom the port labels are not composed at all,
        // which is what took the all-visible case from 1.36 MB to 343 KB.
        const val ALL_VISIBLE_CEILING = 380_000L
        const val EDITOR_VIEW_CEILING = 500_000L
    }
}
