/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeFrameStage
import com.awakekt.awake.compose.ui.platform.ComposeFrameStages
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of a HUD a frame measures, and what that costs (#1621, the `ui:measure` line).
 *
 * The consumer's `ui:measure` is 842 KB a frame on 0.52.0. Measuring is all or nothing per frame: a
 * host with [ComposeHost.skipUnchangedMeasure] off measures every node every frame (all three of the
 * host's skips are opt-in), and one with it
 * on skips a frame only when nothing measuring read changed, so one changed value measures the whole
 * tree again. This reads, for [MeasureAllocationTest]'s HUD, the nodes a frame measures and the bytes
 * its measure stage allocates, with skipping off and on, still and with one value changing a frame.
 * Which of the four the consumer's frames are decides between measuring less (per-node reuse, #1512)
 * and allocating less per node (Row and Column's per-measure arrays). Numbers are printed; only what
 * holds today is asserted, so the numbers are a baseline, not a budget.
 */
class HudMeasureScopeTest {
    private val value = mutableStateOf(0)

    @Test
    fun aFrameMeasuresTheWholeHudUnlessNothingItReadChanged() {
        val cases = listOf(false to false, false to true, true to false, true to true).associateWith { (skipping, ticking) ->
            run(skipping, ticking)
        }
        cases.forEach { (case, reading) ->
            println("HudMeasureScope skipping=${case.first} ticking=${case.second}: $reading")
        }
        val nodes = cases.getValue(false to false).nodesMeasured
        assertTrue(nodes > ROWS * 4, "the HUD has $nodes nodes")
        assertEquals(0, cases.getValue(true to false).nodesMeasured, "a still frame with skipping on measures nothing")
        assertEquals(nodes, cases.getValue(true to true).nodesMeasured, "one changed value measures the whole tree again")
        assertEquals(nodes, cases.getValue(false to true).nodesMeasured)
    }

    private data class Reading(val nodesMeasured: Int, val stageBytes: LongArray, val frameBytes: Long) {
        val measureBytes: Long get() = stageBytes[ComposeFrameStage.Measure.ordinal]

        override fun toString() = "$nodesMeasured nodes measured, frame ${frameBytes / KB} KB: " +
            ComposeFrameStage.entries.joinToString { "${it.name.lowercase()} ${stageBytes[it.ordinal] / KB} KB" } +
            ", rest ${(frameBytes - stageBytes.sum()) / KB} KB " +
            "(${if (nodesMeasured == 0) "-" else "%.1f".format(measureBytes.toDouble() / nodesMeasured)} B a measured node)"
    }

    private fun run(skipping: Boolean, ticking: Boolean): Reading {
        val host = ComposeHost().apply {
            skipUnchangedComposition = skipping
            skipUnchangedMeasure = skipping
            retainUnchangedDraw = skipping
        }
        val stages = StageBytes()
        val input = FrameInput(viewportWidth = WIDTH, viewportHeight = HEIGHT)
        fun frame() {
            if (ticking) value.value += 1
            host.frame(input) { hud() }
        }
        repeat(WARMUP) { frame() }
        host.frameStages = stages
        var least: Reading? = null
        repeat(WINDOWS) {
            stages.bytes.fill(0)
            var measured = 0
            val before = allocated()
            repeat(FRAMES) {
                frame()
                measured += host.frameCounts.nodesMeasured
            }
            val reading = Reading(measured / FRAMES, LongArray(STAGES) { stages.bytes[it] / FRAMES }, (allocated() - before) / FRAMES)
            if (least == null || reading.frameBytes < least!!.frameBytes) least = reading
        }
        host.frameStages = null
        return least!!
    }

    context(_: Composer)
    private fun hud() {
        Column(Modifier.padding(8.dp)) {
            repeat(ROWS) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(16.dp))
                    Text("Health")
                    Text(if (row == 0) "${value.value}" else "100")
                }
            }
        }
    }

    /** Bytes allocated inside each stage, read at its marks only, into an array made up front. */
    private class StageBytes : ComposeFrameStages {
        val bytes = LongArray(STAGES)
        private var started = 0L

        override fun begin(stage: ComposeFrameStage) {
            started = allocated()
        }

        override fun end(stage: ComposeFrameStage) {
            bytes[stage.ordinal] += allocated() - started
        }
    }

    private companion object {
        const val ROWS = 40
        const val WIDTH = 800
        const val HEIGHT = 1200
        const val WARMUP = 300
        const val WINDOWS = 5
        const val FRAMES = 100
        const val KB = 1024
        val STAGES = ComposeFrameStage.entries.size

        fun allocated(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes
    }
}
