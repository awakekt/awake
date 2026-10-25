/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.font.withPixelGlyphMaxSize
import com.awakekt.awake.core.text.theme.TextStyle
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Bytes and milliseconds per frame for text that does not change: `ComposeFrameProbe`'s 20 rows of
 * 3 cells, with a short label in each cell instead of a coloured box.
 *
 * That probe has no text in it, so this cost went unmeasured until a node-graph canvas at rest
 * turned out to spend most of its frame here.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class TextFrameProbe {
    private val painter = Painter()
    private val plainLabels = List(ROWS * CELLS_PER_ROW) { "Label ${it + 1}" }

    /** Labels whose every capital pair kerns in Roboto (#1022): WA, AV, To and Ty. */
    private val kernedLabels = List(ROWS * CELLS_PER_ROW) { "WAVE To Ty ${it + 1}" }
    private var labels = plainLabels
    private var style: TextStyle? = null

    /** The default: labels this small draw exact-size pixel glyphs. */
    @Test
    fun unchangedTextPerFrame() = probe("pixel glyphs", style = null)

    /**
     * The same labels held to the distance field, the path larger or scaled text takes: the two paths
     * are set side by side, since a label switches between them when it crosses the size limit.
     */
    @Test
    fun unchangedDistanceFieldTextPerFrame() = probe("distance field", TextStyle(font = UiFonts.default().withPixelGlyphMaxSize(0)))

    /**
     * Labels full of kerned pairs, kerned and not: the cost of a kerning lookup per adjacent pair, set side
     * by side in one JVM, where a comparison across runs would mostly measure the machine.
     */
    @Test
    fun unchangedKernedTextPerFrame() {
        probe("kerned pairs", style = null, labels = kernedLabels)
        probe("kerned pairs, kerning off", TextStyle(kerning = false), labels = kernedLabels)
    }

    private fun probe(path: String, style: TextStyle?, labels: List<String> = plainLabels) {
        this.style = style
        this.labels = labels
        val root = LayoutNode(ColumnMeasurePolicy())
        repeat(WARMUP_FRAMES) { frame(root) }

        val bytesBefore = allocatedBytes()
        val start = TimeSource.Monotonic.markNow()
        repeat(MEASURED_FRAMES) { frame(root) }
        val elapsed = start.elapsedNow()
        val perFrame = (allocatedBytes() - bytesBefore) / MEASURED_FRAMES
        val msPerFrame = elapsed.inWholeMicroseconds / 1000.0 / MEASURED_FRAMES

        var reconcile = 0L
        var layout = 0L
        var paint = 0L
        repeat(PHASE_SAMPLES) {
            reconcile += measure { composeInto(root) { scene() } }
            layout += measure { root.layoutTree(Constraints.fixed(FRAME_WIDTH, FRAME_HEIGHT)) }
            paint += measure { painter.paint(root) }
        }
        println(
            "TextFrameProbe ($path): $perFrame B/frame, ${"%.3f".format(msPerFrame)} ms/frame for ${labels.size} labels " +
                "(reconcile ${reconcile / PHASE_SAMPLES}, layout ${layout / PHASE_SAMPLES}, " +
                "paint ${paint / PHASE_SAMPLES}); total ${elapsed.inWholeMilliseconds} ms over $MEASURED_FRAMES frames",
        )
        assertTrue(perFrame < CEILING_BYTES_PER_FRAME, "$perFrame B/frame exceeds the $CEILING_BYTES_PER_FRAME B ratchet")
    }

    private fun frame(root: LayoutNode) {
        composeInto(root) { scene() }
        root.layoutTree(Constraints.fixed(FRAME_WIDTH, FRAME_HEIGHT))
        painter.paint(root)
    }

    context(_: Composer)
    private fun scene() {
        repeat(ROWS) { row ->
            val rowModifier = remember { Modifier.height(ROW_HEIGHT.dp) }
            Row(rowModifier) {
                repeat(CELLS_PER_ROW) { cell ->
                    val cellModifier = remember { Modifier.width(CELL_WIDTH.dp) }
                    val label = labels[row * CELLS_PER_ROW + cell]
                    val style = style
                    if (style == null) Text(label, cellModifier) else Text(label, cellModifier, style = style)
                }
            }
        }
    }

    private inline fun measure(block: () -> Unit): Long {
        val before = allocatedBytes()
        block()
        return allocatedBytes() - before
    }

    private fun allocatedBytes(): Long =
        (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val ROWS = 20
        const val CELLS_PER_ROW = 3
        const val ROW_HEIGHT = 36
        const val CELL_WIDTH = 120
        const val FRAME_WIDTH = 800
        const val FRAME_HEIGHT = 900
        const val WARMUP_FRAMES = 2000
        const val MEASURED_FRAMES = 1000
        const val PHASE_SAMPLES = 200

        // Ratchet, not a target: lowered as fixes land. 292,977 B on first measurement; 126,672 after
        // each node started reusing last frame's measurement and glyph run, and no-op style merges
        // stopped allocating. Most of what is left is one Glyph primitive per character in paint.
        //
        // Run alone the figure is exact and repeats. Inside the full desktopTest JVM it has also read
        // 151,345, because escape analysis depends on how earlier tests left the JIT. The ceiling
        // sits above that, still far below the 293 KB a regression back to per-frame re-shaping gives.
        //
        // About 52,000 (pixel glyphs) and 55,000 (distance field) run alone once the weighted family stopped
        // copying a GlyphRect and building a comparator for every glyph it resolves (#993); the distance-field
        // case has read 81,979 inside the full desktopTest JVM, so the ceiling sits above that.
        const val CEILING_BYTES_PER_FRAME = 100_000L
    }
}
