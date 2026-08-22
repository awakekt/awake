// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import com.sun.management.ThreadMXBean
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Box
import io.github.ronjunevaldoz.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Row
import io.github.ronjunevaldoz.awake.compose.foundation.layout.height
import io.github.ronjunevaldoz.awake.compose.foundation.layout.width
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.Painter
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Bytes and milliseconds per frame for the retained engine, on the same scene `ui-core`'s
 * `UiFrameAllocationProbe` uses: 20 rows of 3 cells, 36px tall, 120px wide.
 *
 * The scene is copied deliberately. `awake-ui-performance` Rule 5's first trap is that the scene
 * shape decides what you can see -- a fixed-size scene runs ~2 trial passes per surface in the old
 * engine where a weighted one runs ~7 -- so a number measured on a different shape cannot be set
 * against the old engine's at all.
 *
 * **What this is not.** `ui-core`'s figure covers its whole frame including style resolution through
 * `surface()`; this covers reconcile, layout and paint of `Box` + `background`. Comparable in shape,
 * not yet in work done. The honest cross-engine number needs `09-testing-harness`'s differ running
 * one scene through both.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent, so there is still no
 * allocation measurement off the JVM.
 */
class ComposeFrameProbe {

    @Test
    fun frameAllocationAndTime() {
        val root = LayoutNode(ColumnMeasurePolicy())
        repeat(WARMUP_FRAMES) { frame(root) }

        val bytesBefore = allocatedBytes()
        val start = TimeSource.Monotonic.markNow()
        repeat(MEASURED_FRAMES) { frame(root) }
        val elapsed = start.elapsedNow()
        val bytes = allocatedBytes() - bytesBefore

        val perFrame = bytes / MEASURED_FRAMES
        val msPerFrame = elapsed.inWholeMicroseconds / 1000.0 / MEASURED_FRAMES
        // Always a total beside the average: a warm-up artifact once produced a false 3x claim,
        // and the total is what contradicts it.
        println(
            "ComposeFrameProbe: $perFrame B/frame, ${format(msPerFrame)} ms/frame " +
                "for $ROWS rows x $CELLS_PER_ROW cells (${ROWS * CELLS_PER_ROW} nodes); " +
                "total ${elapsed.inWholeMilliseconds} ms over $MEASURED_FRAMES frames",
        )

        assertTrue(
            perFrame < CEILING_BYTES_PER_FRAME,
            "$perFrame B/frame exceeds the $CEILING_BYTES_PER_FRAME B ratchet",
        )
    }

    @Test
    fun whereTheBytesGo() {
        // Rule 5: the ranking reorders after every fix, so decompose rather than guess. Phases are
        // measured in isolation against an already-warm tree.
        val root = LayoutNode(ColumnMeasurePolicy())
        repeat(WARMUP_FRAMES) { frame(root) }

        val reconcile = measure { composeInto(root) { scene() } }
        val layout = measure { root.layoutTree(Constraints.fixed(FRAME_WIDTH, FRAME_HEIGHT)) }
        val paint = measure { painter.paint(root) }

        println(
            "ComposeFrameProbe phases: reconcile=$reconcile B, layout=$layout B, paint=$paint B " +
                "(total ${reconcile + layout + paint} B)",
        )
    }

    private inline fun measure(block: () -> Unit): Long {
        val before = allocatedBytes()
        repeat(PHASE_SAMPLES) { block() }
        return (allocatedBytes() - before) / PHASE_SAMPLES
    }

    @Test
    fun theTreeIsReusedRatherThanRebuiltEachFrame() {
        // The retained claim, stated as a number: node count must not grow across frames.
        val root = LayoutNode(ColumnMeasurePolicy())
        frame(root)
        val first = root.children[0]
        val afterOne = root.children.size

        repeat(100) { frame(root) }

        assertTrue(root.children.size == afterOne, "child count drifted across 100 frames")
        assertTrue(root.children[0] === first, "row 0 was rebuilt rather than reused")
    }

    /** One full frame: reconcile, layout, paint. No input, so this is the build half only. */
    private fun frame(root: LayoutNode): Int {
        composeInto(root) { scene() }
        root.layoutTree(Constraints.fixed(FRAME_WIDTH, FRAME_HEIGHT))
        return painter.paint(root).size
    }

    context(_: Composer)
    private fun scene() {
        repeat(ROWS) { row ->
            Row(Modifier.height(ROW_HEIGHT.dp)) {
                repeat(CELLS_PER_ROW) { cell ->
                    Box(
                        Modifier.width(CELL_WIDTH.dp)
                            .height(ROW_HEIGHT.dp)
                            .background(if ((row + cell) % 2 == 0) even else odd),
                    )
                }
            }
        }
    }

    private fun format(value: Double): String = ((value * 1000).toInt() / 1000.0).toString()

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

        // Ratchet, not a target -- lower it as each fix lands so a regression cannot hide.
        // 76,170 B on first measurement; 33,289 now, stable to the byte across runs. Fixes so far:
        // cached default policies, chain links re-pointed instead of rebuilt, scratch lists reused,
        // depth arrays copied into rather than reallocated, and the link lists published instead of
        // copied. Still 2.0x the 16 KB ship gate.
        //
        // It caught one: extracting the dim helper dropped its full-opacity early-out, and every
        // quad started allocating a Color -- +2.5 kB/frame, visible only because paint is measured
        // separately. Re-profile after every change, including the ones that look like refactors.
        //
        // What is left, and why it is not a quick win. Reconcile is 17,555 B and most of it is the
        // modifier objects the scene lambda builds fresh each frame; `remember` can now memoize a
        // chain, so this is a consumer-side fix rather than an engine one. Layout is 7,168 B, mostly
        // the per-measure placeable array in the Row/Column policy -- and those policies are shared
        // singletons, so caching scratch on one would corrupt a Row nested inside a Row.
        //
        // Readings were unstable to ~1.4 kB while per-frame allocation remained; they are exact now.
        // Still take three before calling anything a regression, and keep the ratchet above the band.
        const val CEILING_BYTES_PER_FRAME = 35_000L

        val even = Color(0.2f, 0.2f, 0.25f, 1f)
        val odd = Color(0.3f, 0.3f, 0.35f, 1f)

        val painter = Painter()

        private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

        fun allocatedBytes(): Long = threads.currentThreadAllocatedBytes
    }
}
