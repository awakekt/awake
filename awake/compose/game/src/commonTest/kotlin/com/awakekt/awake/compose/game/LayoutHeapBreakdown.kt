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
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeFrameStage
import com.awakekt.awake.compose.ui.platform.ComposeFrameStages
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.dp

/**
 * Which part of [HudMeasureScope]'s HUD allocates in layout, and whether in measuring or in placing
 * (#1621). Each [Part] lays out [ROWS] copies of one piece of the HUD row in a column. A still frame
 * with no skip measures and places every node; one with all three skips only places, because nothing
 * measuring read changed. The layout stage's heap in the second is placement, and the difference is
 * measuring. [heapBytes] is the platform's reading, taken at the stage marks only.
 */
internal class LayoutHeapBreakdown(private val heapBytes: () -> Double) : ComposeFrameStages {
    private var layoutBytes = 0.0
    private var started = 0.0

    override fun begin(stage: ComposeFrameStage) {
        if (stage == ComposeFrameStage.Measure) started = heapBytes()
    }

    override fun end(stage: ComposeFrameStage) {
        if (stage == ComposeFrameStage.Measure) layoutBytes += heapBytes() - started
    }

    /** One piece of the HUD row, repeated [ROWS] times. */
    enum class Part {
        /** `Box(Modifier.size(16.dp))`: one node, one link. */
        Boxes,

        /** `Text("Health")`. */
        Texts,

        /** A box behind `padding(4.dp)`. */
        PaddedBoxes,

        /** A box behind `fillMaxWidth()`. */
        FillWidthBoxes,

        /** A `Row` of three boxes, default arrangement and alignment. */
        Rows,

        /** The HUD's `Row`: `SpaceBetweenHorizontal`, `CenterVertically`, three boxes. */
        SpacedRows,

        /** A box behind `offset { }`, the placement lambda the follower probe names (#1512). */
        OffsetLambdaBoxes,

        /** The whole HUD row: padded, full-width, spaced, a box and two texts. */
        HudRows,
    }

    /** Per frame: nodes measured with no skip, and the layout stage's bytes measuring and placing. */
    class Reading(val nodes: Int, val measureBytes: Double, val placeBytes: Double) {
        override fun toString(): String =
            "$nodes nodes, layout ${kb(measureBytes + placeBytes)} KB: measure ${kb(measureBytes)} KB " +
                "(${perNode(measureBytes)} B a node), place ${kb(placeBytes)} KB (${perNode(placeBytes)} B a node)"

        private fun perNode(bytes: Double) = if (nodes == 0) "-" else tenths(bytes / nodes)
    }

    /** [part] read on a still frame with no skip and with all three, the quieter of [windows] each. */
    fun read(part: Part, warmUp: Int, windows: Int, frames: Int, collect: () -> Unit): Reading {
        val full = layout(part, skip = false, warmUp, windows, frames, collect)
        val placed = layout(part, skip = true, warmUp, windows, frames, collect)
        return Reading(full.first, (full.second - placed.second).coerceAtLeast(0.0), placed.second)
    }

    private fun layout(part: Part, skip: Boolean, warmUp: Int, windows: Int, frames: Int, collect: () -> Unit): Pair<Int, Double> {
        val host = ComposeHost().apply {
            skipUnchangedComposition = skip
            skipUnchangedMeasure = skip
            retainUnchangedDraw = skip
        }
        val input = FrameInput(viewportWidth = WIDTH, viewportHeight = HEIGHT)
        repeat(warmUp) { host.frame(input) { content(part) } }
        host.frameStages = this
        var least = Double.MAX_VALUE
        var nodes = 0
        repeat(windows) {
            layoutBytes = 0.0
            collect()
            var measured = 0
            repeat(frames) {
                host.frame(input) { content(part) }
                measured += host.frameCounts.nodesMeasured
            }
            nodes = measured / frames
            least = minOf(least, layoutBytes / frames)
        }
        host.frameStages = null
        return nodes to least
    }

    context(_: Composer)
    private fun content(part: Part) {
        Column(Modifier.padding(8.dp)) {
            repeat(ROWS) { row ->
                when (part) {
                    Part.Boxes -> Box(Modifier.size(16.dp))
                    Part.Texts -> Text("Health")
                    Part.PaddedBoxes -> Box(Modifier.padding(4.dp).size(16.dp))
                    Part.FillWidthBoxes -> Box(Modifier.fillMaxWidth().size(16.dp))
                    Part.Rows -> Row { threeBoxes() }
                    Part.SpacedRows -> Row(
                        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                        verticalAlignment = Alignment.CenterVertically,
                    ) { threeBoxes() }
                    Part.OffsetLambdaBoxes -> Box(Modifier.offset { IntOffset(row % 4, row) }.size(16.dp))
                    Part.HudRows -> Row(
                        Modifier.fillMaxWidth().padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(16.dp))
                        Text("Health")
                        Text("100")
                    }
                }
            }
        }
    }

    context(_: Composer)
    private fun threeBoxes() {
        Box(Modifier.size(16.dp))
        Box(Modifier.size(16.dp))
        Box(Modifier.size(16.dp))
    }

    companion object {
        const val ROWS = HudMeasureScope.ROWS
        private const val WIDTH = 800
        private const val HEIGHT = 1200

        private fun kb(bytes: Double): String = tenths(bytes / 1024)

        private fun tenths(value: Double): String = ((value * 10).toLong() / 10.0).toString()
    }
}
