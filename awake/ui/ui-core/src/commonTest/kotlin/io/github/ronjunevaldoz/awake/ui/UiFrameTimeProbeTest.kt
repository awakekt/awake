// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui

import io.github.ronjunevaldoz.awake.core.math2d.px
import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.ui.context.UiContext
import io.github.ronjunevaldoz.awake.ui.context.UiFrameInput
import io.github.ronjunevaldoz.awake.ui.context.UiMeasureTrialStats
import io.github.ronjunevaldoz.awake.ui.layouts.Arrangement
import io.github.ronjunevaldoz.awake.ui.layouts.ColumnScope
import io.github.ronjunevaldoz.awake.ui.layouts.column
import io.github.ronjunevaldoz.awake.ui.layouts.row
import io.github.ronjunevaldoz.awake.ui.layouts.surface
import io.github.ronjunevaldoz.awake.ui.modifier.Modifier
import io.github.ronjunevaldoz.awake.ui.modifier.height
import io.github.ronjunevaldoz.awake.ui.modifier.weight
import io.github.ronjunevaldoz.awake.ui.modifier.width
import io.github.ronjunevaldoz.awake.ui.theme.UiDefaultTheme
import io.github.ronjunevaldoz.awake.ui.theme.neutralStyle
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * UI build time for one fixed scene, measured with the same code on every target.
 *
 * The point is the cross-platform ratio, not the absolute number. Desktop JVM is the forgiving
 * platform -- it has a large young generation and a mature JIT -- so a UI cost that looks
 * acceptable there can be several times worse on wasm, and until this existed nothing measured
 * that. Run it on both to get the multiplier:
 *
 *   ./gradlew :awake:ui:ui-core:desktopTest --tests "*UiFrameTimeProbeTest*" -i
 *   ./gradlew :awake:ui:ui-core:wasmJsBrowserTest --tests "*UiFrameTimeProbeTest*" -i
 *
 * Deliberately layout-only: no renderer, no GPU, no `requestAnimationFrame`. It measures the
 * phase `SceneAppLifecycleRuntime` reports as `uiBuildMs`, which is the half of a frame that a
 * headless browser test can measure honestly. GPU submission needs a real presenting window.
 *
 * Sibling to `UiFrameAllocationProbe`, which measures bytes and is desktop-only because its
 * counter (`ThreadMXBean`) is a JVM API with no wasm equivalent.
 */
class UiFrameTimeProbeTest {

    @Test
    fun reportsUiBuildTimePerFrame() {
        val ui = UiContext()
        repeat(WARMUP_FRAMES) { renderFrame(ui) }

        val start = TimeSource.Monotonic.markNow()
        repeat(MEASURED_FRAMES) { renderFrame(ui) }
        val totalMs = start.elapsedNow().inWholeMicroseconds / 1000f
        val perFrameMs = totalMs / MEASURED_FRAMES

        UiMeasureTrialStats.reset()
        UiMeasureTrialStats.enabled = true
        try {
            renderFrame(ui)
        } finally {
            UiMeasureTrialStats.enabled = false
        }

        println(
            "UiFrameTimeProbe: uiBuild=${perFrameMs}ms/frame over ${ROWS * CELLS_PER_ROW} surfaces, " +
                "${UiMeasureTrialStats.trialCount} trial passes " +
                "(total ${totalMs}ms for $MEASURED_FRAMES frames)",
        )
    }

    private fun renderFrame(ui: UiContext) {
        ui.beginFrame(
            UiFrameInput(
                viewportWidth = FRAME_WIDTH,
                viewportHeight = FRAME_HEIGHT,
                input = UiInputState(pointerX = -100f, pointerY = -100f),
            ),
        )
        ui.createAbsolute(x = 0f, y = 0f).column(id = "probe-root") {
            repeat(ROWS) { rowIndex -> panelRow(rowIndex) }
        }
        ui.finishFrame()
    }

    /** Weighted children under a distributing arrangement -- the shape real pages have, and the
     * one that actually drives trial passes. */
    private fun ColumnScope.panelRow(rowIndex: Int) {
        row(
            horizontalArrangement = Arrangement.SpaceBetween,
            id = "row-$rowIndex",
            modifier = Modifier.width(Dimension.FillMax).height(ROW_HEIGHT.px),
        ) {
            repeat(CELLS_PER_ROW) { cellIndex ->
                surface(
                    id = "cell-$rowIndex-$cellIndex",
                    style = UiDefaultTheme.colors.neutralStyle(),
                    modifier = Modifier.weight(1f).height(Dimension.FillMax),
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

        // Lower than UiFrameAllocationProbe's counts: this runs in a browser under karma too,
        // where a multi-second test body reads as a hang.
        const val WARMUP_FRAMES = 200
        const val MEASURED_FRAMES = 200
    }
}
