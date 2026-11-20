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

/**
 * How much of a HUD a frame measures, composes and draws, and what each stage allocates (#1621).
 *
 * The HUD is [MeasureAllocationTest]'s: [ROWS] stat rows, one of whose values can change every frame.
 * [heapBytes] is the platform's reading (allocated bytes on the JVM, used heap in a browser), taken
 * at the frame stage marks only, into an array made up front, so the probe allocates nothing between
 * readings. Read against each other, the two platforms name the stage whose allocation is Wasm-only.
 */
internal class HudMeasureScope(private val heapBytes: () -> Double) : ComposeFrameStages {
    private val value = mutableStateOf(0)
    private val stageBytes = DoubleArray(STAGES)
    private var started = 0.0

    override fun begin(stage: ComposeFrameStage) {
        started = heapBytes()
    }

    override fun end(stage: ComposeFrameStage) {
        stageBytes[stage.ordinal] += heapBytes() - started
    }

    /** One case's reading per frame: the nodes measured, each stage's bytes and the rest of the frame. */
    class Reading(val nodesMeasured: Int, val stageBytes: DoubleArray, val frameBytes: Double) {
        val measureBytes: Double get() = stageBytes[ComposeFrameStage.Measure.ordinal]

        override fun toString(): String =
            "$nodesMeasured nodes measured, frame ${kb(frameBytes)} KB: " +
                ComposeFrameStage.entries.joinToString { "${it.name.lowercase()} ${kb(stageBytes[it.ordinal])} KB" } +
                ", rest ${kb(frameBytes - stageBytes.sum())} KB, " +
                (if (nodesMeasured == 0) "-" else tenths(measureBytes / nodesMeasured)) + " B a measured node"
    }

    /** Which of [ComposeHost]'s three opt-in skips a case turns on. */
    enum class Skips(val composition: Boolean, val measure: Boolean, val draw: Boolean) {
        /** None, the host's default. */
        Off(false, false, false),

        /** Only [ComposeHost.retainUnchangedDraw]: the consumer's released builds (20 Nov). */
        DrawOnly(false, false, true),

        /** All three. */
        All(true, true, true),
    }

    /**
     * The quietest of [windows] windows of [frames] frames, after [warmUp], on a host with [skips], with
     * one value changing every frame when [ticking]. [collect] runs before each window, where the
     * platform can collect garbage.
     */
    fun read(skips: Skips, ticking: Boolean, warmUp: Int, windows: Int, frames: Int, collect: () -> Unit): Reading {
        val host = ComposeHost().apply {
            skipUnchangedComposition = skips.composition
            skipUnchangedMeasure = skips.measure
            retainUnchangedDraw = skips.draw
        }
        val input = FrameInput(viewportWidth = WIDTH, viewportHeight = HEIGHT)
        fun frame() {
            if (ticking) value.value += 1
            host.frame(input) { hud() }
        }
        repeat(warmUp) { frame() }
        host.frameStages = this
        var least: Reading? = null
        repeat(windows) {
            stageBytes.fill(0.0)
            collect()
            var measured = 0
            val before = heapBytes()
            repeat(frames) {
                frame()
                measured += host.frameCounts.nodesMeasured
            }
            val total = heapBytes() - before
            val reading = Reading(measured / frames, DoubleArray(STAGES) { stageBytes[it] / frames }, total / frames)
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

    companion object {
        const val ROWS = 40
        private const val WIDTH = 800
        private const val HEIGHT = 1200
        private val STAGES = ComposeFrameStage.entries.size

        /** The cases, as skips to ticking, in the order a report lists them. */
        val Cases: List<Pair<Skips, Boolean>> = Skips.entries.flatMap { skips -> listOf(skips to false, skips to true) }

        private fun kb(bytes: Double): String = tenths(bytes / 1024)

        private fun tenths(value: Double): String = ((value * 10).toLong() / 10.0).toString()
    }
}
