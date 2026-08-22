// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui

import io.github.ronjunevaldoz.awake.core.math2d.px
import com.sun.management.ThreadMXBean
import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.ui.context.UiContext
import io.github.ronjunevaldoz.awake.ui.context.UiFrameInput
import io.github.ronjunevaldoz.awake.ui.context.UiMeasureTrialStats
import io.github.ronjunevaldoz.awake.ui.layouts.Arrangement
import io.github.ronjunevaldoz.awake.ui.layouts.ColumnScope
import io.github.ronjunevaldoz.awake.ui.layouts.column
import io.github.ronjunevaldoz.awake.ui.layouts.defaultArrangement
import io.github.ronjunevaldoz.awake.ui.layouts.row
import io.github.ronjunevaldoz.awake.ui.layouts.surface
import io.github.ronjunevaldoz.awake.ui.modifier.Modifier
import io.github.ronjunevaldoz.awake.ui.modifier.height
import io.github.ronjunevaldoz.awake.ui.modifier.weight
import io.github.ronjunevaldoz.awake.ui.modifier.width
import io.github.ronjunevaldoz.awake.ui.theme.UiDefaultTheme
import io.github.ronjunevaldoz.awake.ui.theme.neutralStyle
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bytes allocated per frame by a fixed ui-core-only scene.
 *
 * This engine rebuilds every frame from scratch (see `UiLocal.kt`) -- there is no composition and
 * nothing to invalidate, so allocation-per-frame *is* the frame cost that a GC eventually charges
 * back. Sibling to `:awake:ui:benchmark`'s `LayoutNestingBenchmark`, and split from it on that
 * benchmark's own stated rule: timing lives in the benchmark, exact counts live in a test where
 * they are noise-free. Allocated bytes are an exact cumulative counter, unaffected by when GC runs.
 *
 * Deliberately widget-free (no theme provider, no text, no design system) so a regression here
 * cannot be blamed on a recipe.
 */
class UiFrameAllocationProbe {

    @Test
    fun frameAllocationStaysUnderCeiling() {
        val perFrame = measureScene(ROWS)
        val primitives = UiContext().let { warmAndRender(it, ROWS) }

        println(
            "UiFrameAllocationProbe: $perFrame bytes/frame over $primitives primitives " +
                "(${perFrame / primitives} bytes/primitive)",
        )
        assertTrue(
            perFrame <= CEILING_BYTES_PER_FRAME,
            "frame allocated $perFrame bytes, ceiling is $CEILING_BYTES_PER_FRAME",
        )
    }

    /**
     * Splits the per-frame total into an empty-frame floor and a marginal per-row slope, so a fix
     * can be aimed: a big floor is frame-setup cost, a big slope is per-node cost.
     */
    @Test
    fun frameAllocationDecomposition() {
        val empty = measureScene(rows = 0)
        val small = measureScene(rows = 10)
        val full = measureScene(rows = ROWS)
        val perRow = (full - small) / (ROWS - 10)

        println(
            "UiFrameAllocationProbe decomposition: empty=$empty B, 10 rows=$small B, " +
                "$ROWS rows=$full B, marginal=$perRow B/row (${perRow / CELLS_PER_ROW} B/surface)",
        )
    }

    /**
     * Trial-measure passes per frame. Every per-node allocation is multiplied by this, so it is
     * the number to attack once per-site shaving stops paying.
     */
    @Test
    fun trialPassesPerFrame() {
        val ui = UiContext()
        warmAndRender(ui, ROWS)
        UiMeasureTrialStats.reset()
        UiMeasureTrialStats.enabled = true
        try {
            renderFrame(ui, ROWS)
        } finally {
            UiMeasureTrialStats.enabled = false
        }
        val trials = UiMeasureTrialStats.trialCount
        println(
            "UiFrameAllocationProbe: $trials trial passes/frame for $ROWS rows x $CELLS_PER_ROW " +
                "surfaces (${trials.toFloat() / (ROWS * CELLS_PER_ROW)} per surface)",
        )
    }

    /**
     * The same scene sized the way real pages are -- weighted children under a distributing
     * arrangement -- which is what actually drives trial passes. The fixed-size scene above
     * barely trials at all, so it measures per-node cost with the multiplier switched off; this
     * one measures it switched on. Reported together, the pair says whether a given fix helps
     * the constant or the multiplier.
     */
    @Test
    fun weightedSceneCostsMoreTrials() {
        val ui = UiContext()
        repeat(WARMUP_FRAMES) { renderFrame(ui, ROWS, weighted = true) }
        val before = allocatedBytes()
        repeat(MEASURED_FRAMES) { renderFrame(ui, ROWS, weighted = true) }
        val perFrame = (allocatedBytes() - before) / MEASURED_FRAMES

        UiMeasureTrialStats.reset()
        UiMeasureTrialStats.enabled = true
        try {
            renderFrame(ui, ROWS, weighted = true)
        } finally {
            UiMeasureTrialStats.enabled = false
        }
        println(
            "UiFrameAllocationProbe weighted: $perFrame bytes/frame, " +
                "${UiMeasureTrialStats.trialCount} trial passes/frame",
        )
    }

    /**
     * The same two scenes with the opt-in cross-frame trial cache switched on -- `id` alone does
     * nothing, `cacheKey` is what arms it (see UiWeightCache.resolveHasWeightedChild). A constant
     * key is honest here because this content's weight()-usage never changes.
     */
    @Test
    fun cacheKeyEffectOnTrials() {
        for (weighted in listOf(false, true)) {
            for (key in listOf(null, "static")) {
                val ui = UiContext()
                repeat(WARMUP_FRAMES) { renderFrame(ui, ROWS, weighted, key) }
                val before = allocatedBytes()
                repeat(MEASURED_FRAMES) { renderFrame(ui, ROWS, weighted, key) }
                val perFrame = (allocatedBytes() - before) / MEASURED_FRAMES
                UiMeasureTrialStats.reset()
                UiMeasureTrialStats.enabled = true
                try {
                    renderFrame(ui, ROWS, weighted, key)
                } finally {
                    UiMeasureTrialStats.enabled = false
                }
                println(
                    "UiFrameAllocationProbe cacheKey: weighted=$weighted key=$key -> " +
                        "$perFrame B/frame, ${UiMeasureTrialStats.trialCount} trials",
                )
            }
        }
    }

    private fun measureScene(rows: Int): Long {
        val ui = UiContext()
        warmAndRender(ui, rows)
        val before = allocatedBytes()
        repeat(MEASURED_FRAMES) { renderFrame(ui, rows) }
        return (allocatedBytes() - before) / MEASURED_FRAMES
    }

    private fun warmAndRender(ui: UiContext, rows: Int): Int {
        repeat(WARMUP_FRAMES) { renderFrame(ui, rows) }
        return renderFrame(ui, rows)
    }

    /** Rows of sized surfaces -- the shape a real panel has, without any widget vocabulary. */
    private fun renderFrame(ui: UiContext, rows: Int, weighted: Boolean = false, cacheKey: Any? = null): Int {
        ui.beginFrame(
            UiFrameInput(
                viewportWidth = FRAME_WIDTH,
                viewportHeight = FRAME_HEIGHT,
                input = UiInputState(pointerX = -100f, pointerY = -100f),
            ),
        )
        ui.createAbsolute(x = 0f, y = 0f).column(id = "probe-root", cacheKey = cacheKey) {
            repeat(rows) { rowIndex -> panelRow(rowIndex, weighted, cacheKey) }
        }
        return ui.finishFrame().primitives.size
    }

    private fun ColumnScope.panelRow(rowIndex: Int, weighted: Boolean, cacheKey: Any?) {
        row(
            horizontalArrangement = if (weighted) Arrangement.SpaceBetween else defaultArrangement(),
            id = "row-$rowIndex",
            cacheKey = cacheKey,
            modifier = Modifier.width(Dimension.FillMax).height(ROW_HEIGHT.px),
        ) {
            repeat(CELLS_PER_ROW) { cellIndex ->
                surface(
                    id = "cell-$rowIndex-$cellIndex",
                    style = UiDefaultTheme.colors.neutralStyle(),
                    modifier = if (weighted) {
                        Modifier.weight(1f).height(Dimension.FillMax)
                    } else {
                        Modifier.width(CELL_WIDTH.px).height(Dimension.FillMax)
                    },
                ) {}
            }
        }
    }

    private companion object {
        const val FRAME_WIDTH = 1280f
        const val FRAME_HEIGHT = 900f
        const val ROWS = 20
        const val CELLS_PER_ROW = 3
        const val ROW_HEIGHT = 36f
        const val CELL_WIDTH = 120f
        const val WARMUP_FRAMES = 2000
        const val MEASURED_FRAMES = 1000

        // Ratchet, not a target -- lower it as each allocation fix lands, so a fix cannot be
        // silently undone later. 2026-08-21: 1,151,016 B/frame, down to 507,357 B over five
        // fixes (weight-answer read-through, ambient copy without a snapshot object, no clip
        // tessellation during trials, indexed style resolution, shared empty modifier plus
        // identity-guarded width/height).
        const val CEILING_BYTES_PER_FRAME = 550_000L

        private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean

        fun allocatedBytes(): Long = threads.currentThreadAllocatedBytes
    }
}
