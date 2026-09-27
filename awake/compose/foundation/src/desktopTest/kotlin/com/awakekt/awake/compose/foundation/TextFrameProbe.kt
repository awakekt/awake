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
    private val labels = List(ROWS * CELLS_PER_ROW) { "Label ${it + 1}" }

    @Test
    fun unchangedTextPerFrame() {
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
            "TextFrameProbe: $perFrame B/frame, ${"%.3f".format(msPerFrame)} ms/frame for ${labels.size} labels " +
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
                    Text(labels[row * CELLS_PER_ROW + cell], cellModifier)
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
        const val CEILING_BYTES_PER_FRAME = 165_000L
    }
}
