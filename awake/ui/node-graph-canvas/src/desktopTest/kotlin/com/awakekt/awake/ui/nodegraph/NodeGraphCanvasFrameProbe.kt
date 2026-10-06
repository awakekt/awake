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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    private val allocationCounter = ManagementFactory.getThreadMXBean() as ThreadMXBean

    // A volatile write makes the control's extra allocation observable to the JVM optimizer.
    @Volatile
    private var allocationSink: ByteArray? = null

    @Test
    fun everyNodeOnScreen() = probe("all 200 visible, zoom 0.25", NodeGraphViewport(zoom = 0.25f), ALL_VISIBLE_CEILING)

    @Test
    fun editorView() = probe("editor view, zoom 1", NodeGraphViewport(zoom = 1f), EDITOR_VIEW_CEILING)

    @Test
    fun sustainedAllocationRegressionStillFails() {
        val failure = assertFailsWith<AssertionError> {
            probe(
                "allocation control, zoom 1",
                NodeGraphViewport(zoom = 1f),
                EDITOR_VIEW_CEILING,
                extraBytesPerFrame = EDITOR_VIEW_CEILING.toInt(),
            )
        }
        assertTrue(failure.message.orEmpty().contains("windows="), failure.message)
        assertTrue(failure.message.orEmpty().contains("ceiling=$EDITOR_VIEW_CEILING"), failure.message)
    }

    private fun probe(label: String, viewport: NodeGraphViewport, ceiling: Long, extraBytesPerFrame: Int = 0) {
        val root = LayoutNode(ColumnMeasurePolicy())
        var primitives = 0
        repeat(WARMUP_FRAMES) { primitives = frame(root, viewport) }

        // One early window can straddle JIT compilation. Take a fixed set, never retry until green.
        // Read the counters before constructing result objects or formatting the diagnostics.
        val samples = List(MEASUREMENT_WINDOWS) {
            val bytesBefore = allocatedBytes()
            val start = System.nanoTime()
            repeat(MEASURED_FRAMES) {
                frame(root, viewport)
                if (extraBytesPerFrame > 0) allocationSink = ByteArray(extraBytesPerFrame)
            }
            val bytes = allocatedBytes() - bytesBefore
            val elapsed = System.nanoTime() - start
            Measurement(bytes / MEASURED_FRAMES, elapsed)
        }
        allocationSink = null
        val windows = samples.map { it.bytesPerFrame }
        val median = windows.sorted()[MEASUREMENT_WINDOWS / 2]
        val totalMillis = samples.sumOf { it.elapsedNanos } / 1_000_000.0
        val measuredFrames = MEASUREMENT_WINDOWS * MEASURED_FRAMES
        val report = "NodeGraphCanvasFrameProbe [$label]: median=$median B/frame, ceiling=$ceiling B/frame, " +
            "windows=$windows; $primitives primitives; total ${"%.3f".format(totalMillis)} ms over " +
            "$measuredFrames measured frames ($MEASUREMENT_WINDOWS x $MEASURED_FRAMES), " +
            "${"%.3f".format(totalMillis / measuredFrames)} ms/frame; warm-up=$WARMUP_FRAMES; " +
            "JVM=${System.getProperty("java.runtime.version")}"
        println(report)
        assertTrue(median < ceiling, "Allocation ratchet exceeded: $report")
    }

    private fun frame(root: LayoutNode, viewport: NodeGraphViewport): Int {
        composeInto(root) {
            NodeGraphCanvas(graph, registry, viewport, emptySet(), {}, Modifier.fillMaxSize())
        }
        root.layoutTree(Constraints.fixed(FRAME_WIDTH, FRAME_HEIGHT))
        return painter.paint(root).size
    }

    private fun allocatedBytes(): Long = allocationCounter.currentThreadAllocatedBytes

    private data class Measurement(val bytesPerFrame: Long, val elapsedNanos: Long)

    private companion object {
        const val NODES = 200
        const val COLUMNS = 20
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 800
        const val WARMUP_FRAMES = 2_000
        const val MEASURED_FRAMES = 300
        const val MEASUREMENT_WINDOWS = 5

        // Keep the original ceilings; the median rejects sustained regressions while tolerating
        // isolated warm-up/compilation windows. These are ratchets, lowered as fixes land.
        // First measured 2026-09-27 at 343 KB (all visible) and 451 KB (editor view) per frame. The
        // editor view fell to 257-267 KB once unchanged Text stopped re-shaping every frame; the
        // all-visible view, which composes almost no text below labelMinZoom, stayed at 334-359 KB.
        //
        // The canvas's own share is small: idle wires redraw retained meshes, and below
        // labelMinZoom the port labels are not composed at all. What remains is mostly the engine's
        // per-node work, chiefly one primitive per glyph.
        const val ALL_VISIBLE_CEILING = 380_000L
        const val EDITOR_VIEW_CEILING = 295_000L
    }
}
