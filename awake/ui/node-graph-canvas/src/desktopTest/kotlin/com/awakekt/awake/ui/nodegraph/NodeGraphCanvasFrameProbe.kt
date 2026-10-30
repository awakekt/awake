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
import com.sun.management.HotSpotDiagnosticMXBean
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bytes and milliseconds per frame for a 200-node, 199-wire graph at rest, on the same
 * reconcile + layout + paint loop `ComposeFrameProbe` measures, so the numbers can be set beside
 * that probe's.
 *
 * Two views, because culling decides what a frame costs: every node on screen at zoom 0.25 is the
 * worst case, and zoom 1 over the same graph is what an editor usually shows.
 *
 * Each view warms up, keeps going until two windows agree, then takes seven windows and fails only
 * when every one of them is over the ceiling. It runs with escape analysis off, so the count is the
 * code's and not the JIT's: see the ceilings.
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
        assertFalse(escapeAnalysisEnabled(), ESCAPE_ANALYSIS_REQUIRED)
        val root = LayoutNode(ColumnMeasurePolicy())
        var primitives = 0
        repeat(WARMUP_FRAMES) { primitives = frame(root, viewport) }

        // Keep warming until two windows in a row agree, so no measured window straddles a
        // compilation. Bounded, and never a retry: the windows below are taken exactly once.
        var settleWindows = 1
        var previous = window(root, viewport, extraBytesPerFrame).bytesPerFrame
        var settled = false
        while (!settled && settleWindows < MAX_SETTLE_WINDOWS) {
            val next = window(root, viewport, extraBytesPerFrame).bytesPerFrame
            settleWindows++
            settled = abs(next - previous) <= SETTLED_BYTES_PER_FRAME
            previous = next
        }

        val samples = List(MEASUREMENT_WINDOWS) { window(root, viewport, extraBytesPerFrame) }
        allocationSink = null
        val windows = samples.map { it.bytesPerFrame }
        val sorted = windows.sorted()
        val lowest = sorted.first()
        val noiseBand = sorted.last() - lowest
        val totalMillis = samples.sumOf { it.elapsedNanos } / 1_000_000.0
        val measuredFrames = MEASUREMENT_WINDOWS * MEASURED_FRAMES
        val report = "NodeGraphCanvasFrameProbe [$label]: min=$lowest B/frame, " +
            "median=${sorted[MEASUREMENT_WINDOWS / 2]} B/frame, max=${sorted.last()} B/frame, " +
            "noise band=$noiseBand B/frame, ceiling=$ceiling B/frame, windows=$windows; " +
            "$primitives primitives; total ${"%.3f".format(totalMillis)} ms over " +
            "$measuredFrames measured frames ($MEASUREMENT_WINDOWS x $MEASURED_FRAMES), " +
            "${"%.3f".format(totalMillis / measuredFrames)} ms/frame; warm-up=$WARMUP_FRAMES frames + " +
            "$settleWindows settle windows (settled=$settled); escape analysis off; " +
            "JVM=${System.getProperty("java.runtime.version")}"
        println(report)
        // A per-frame allocation is paid in every window, so it lifts the lowest one too.
        assertTrue(lowest < ceiling, "Allocation ratchet exceeded in every window: $report")
    }

    /** One window of [MEASURED_FRAMES] frames. The counters are read before anything is built from them. */
    private fun window(root: LayoutNode, viewport: NodeGraphViewport, extraBytesPerFrame: Int): Measurement {
        val bytesBefore = allocatedBytes()
        val start = System.nanoTime()
        repeat(MEASURED_FRAMES) {
            frame(root, viewport)
            if (extraBytesPerFrame > 0) allocationSink = ByteArray(extraBytesPerFrame)
        }
        val bytes = allocatedBytes() - bytesBefore
        val elapsed = System.nanoTime() - start
        return Measurement(bytes / MEASURED_FRAMES, elapsed)
    }

    private fun frame(root: LayoutNode, viewport: NodeGraphViewport): Int {
        composeInto(root) {
            NodeGraphCanvas(graph, registry, viewport, emptySet(), {}, Modifier.fillMaxSize())
        }
        root.layoutTree(Constraints.fixed(FRAME_WIDTH, FRAME_HEIGHT))
        return painter.paint(root).size
    }

    private fun allocatedBytes(): Long = allocationCounter.currentThreadAllocatedBytes

    private fun escapeAnalysisEnabled(): Boolean = ManagementFactory
        .getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
        .getVMOption("DoEscapeAnalysis")
        .value
        .toBoolean()

    private data class Measurement(val bytesPerFrame: Long, val elapsedNanos: Long)

    private companion object {
        const val NODES = 200
        const val COLUMNS = 20
        const val FRAME_WIDTH = 1280
        const val FRAME_HEIGHT = 800
        const val WARMUP_FRAMES = 2_000
        const val MEASURED_FRAMES = 300
        const val MEASUREMENT_WINDOWS = 7
        const val MAX_SETTLE_WINDOWS = 10
        const val SETTLED_BYTES_PER_FRAME = 256L

        const val ESCAPE_ANALYSIS_REQUIRED = "NodeGraphCanvasFrameProbe needs -XX:-DoEscapeAnalysis, which " +
            "the module's desktopTest task passes. Run it through Gradle: with escape analysis on, the count " +
            "depends on what the JIT happened to scalar-replace in this JVM, not on the code (#945)."

        // These count every byte the frame asks for. The test JVM runs with escape analysis off
        // (build.gradle.kts), because with it on the count depended on which per-node temporaries C2
        // happened to scalar-replace in that JVM: the same commit read 346,920, 366,152 or 385,384
        // B/frame (all visible) and 214,384 to 265,856 (editor view) across CI runs, while every window
        // inside one run agreed to the byte (#945). With it off, ten local runs and every window in
        // them read 606,856 and 521,552 exactly. That is also closer to what the frame costs where
        // allocation hurts, on ART and Kotlin/Native, which have no C2 to remove the temporaries.
        //
        // Ratchets, lowered as fixes land; each sits about 2% above the last measurement.
        // The bytes are mostly the engine's per-node work, chiefly one primitive per glyph. The
        // canvas's own share is small: idle wires redraw retained meshes, and below labelMinZoom the
        // port labels are not composed at all. The editor view read 527,600 until the weighted font stopped
        // allocating per glyph it resolves (#993), then 341,624, and 347,672 before #1061. #1061 gives every
        // glyph primitive a `decoration` reference, its outline and shadow, which takes a `UiDrawPrimitive.Glyph`
        // from 64 to 72 bytes: 402 glyphs a frame here, 3,216 bytes, read 350,888. The all-visible view draws
        // no glyphs at zoom 0.25 and did not move.
        const val ALL_VISIBLE_CEILING = 620_000L
        const val EDITOR_VIEW_CEILING = 358_000L
    }
}
