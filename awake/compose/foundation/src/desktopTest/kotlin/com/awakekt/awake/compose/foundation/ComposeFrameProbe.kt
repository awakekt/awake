/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.sun.management.ThreadMXBean
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
            val rowModifier = remember { Modifier.height(ROW_HEIGHT.dp) }
            Row(rowModifier) {
                repeat(CELLS_PER_ROW) { cell ->
                    val cellModifier = remember {
                        Modifier.width(CELL_WIDTH.dp)
                            .height(ROW_HEIGHT.dp)
                            .background(if ((row + cell) % 2 == 0) even else odd)
                    }
                    Box(
                        cellModifier,
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
        // 76,170 B on first measurement; 33,289 was a prior baseline. Fixes so far:
        // cached default policies, chain links re-pointed instead of rebuilt, scratch lists reused,
        // depth arrays copied into rather than reallocated, and the link lists published instead of
        // copied. The 35 KB limit became stale: commit 55354c69, before the current Compose work,
        // measured 38,076 B/frame on this same machine and scene. Keep a narrow ceiling above that
        // observed baseline so later regressions remain visible.
        //
        // It caught one: extracting the dim helper dropped its full-opacity early-out, and every
        // quad started allocating a Color -- +2.5 kB/frame, visible only because paint is measured
        // separately. Re-profile after every change, including the ones that look like refactors.
        //
        // Static modifier chains are remembered in this scene. That both measures the retained
        // lifecycle under its intended use and keeps the ratchet focused on reconciliation, layout,
        // and paint rather than allocations a caller can avoid. Layout is 7,168 B, mostly the
        // per-measure placeable array in the Row/Column policy -- and those policies are shared
        // singletons, so caching scratch on one would corrupt a Row nested inside a Row.
        //
        // Readings are stable to a byte once warm. Still take three before calling anything a
        // regression, and keep the ratchet above the measured band.
        const val CEILING_BYTES_PER_FRAME = 39_000L

        val even = Color(0.2f, 0.2f, 0.25f, 1f)
        val odd = Color(0.3f, 0.3f, 0.35f, 1f)

        val painter = Painter()

        private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

        fun allocatedBytes(): Long = threads.currentThreadAllocatedBytes
    }
}
