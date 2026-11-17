/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeFrameStage
import com.awakekt.awake.compose.ui.platform.ComposeFrameStages
import com.awakekt.awake.compose.ui.platform.ComposeHost

/**
 * Where a skipped [FollowerHud] frame's heap goes, stage by stage (#1512): compose, measure (on a
 * skipped frame, placement only), draw, and the rest of `ComposeHost.frame`, which is input, focus
 * and the semantics tree. [heapBytes] is the platform's reading: allocated bytes on the JVM, used
 * heap in a browser. Read against each other, the two name the stage whose allocation is Wasm-only.
 *
 * Readings are taken at the stage marks, so nothing between them allocates for the probe: the sums
 * go into a [DoubleArray] made up front.
 */
internal class FollowerStageHeap(private val heapBytes: () -> Double) : ComposeFrameStages {
    private val sums = DoubleArray(STAGES + 1)
    private var started = 0.0

    override fun begin(stage: ComposeFrameStage) {
        started = heapBytes()
    }

    override fun end(stage: ComposeFrameStage) {
        sums[stage.ordinal] += heapBytes() - started
    }

    /**
     * KB per skipped frame for each stage of a 200-plate HUD made of [parts], and for the rest of the
     * frame, over [frames] frames after [warmUp], with its nodes [moving] or still. [collect] runs once
     * before the measured window, where the platform can collect garbage.
     */
    fun measure(moving: Boolean, warmUp: Int, frames: Int, collect: () -> Unit, parts: FollowerParts = FollowerParts.All): DoubleArray {
        val hud = FollowerHud(PLATES, parts)
        return measure(warmUp, frames, collect) { hud.frame(it, moving) }
    }

    /** [measure] for any content [frame] draws into a skipping host. */
    fun measure(warmUp: Int, frames: Int, collect: () -> Unit, frame: (ComposeHost) -> Unit): DoubleArray {
        val host = ComposeHost().apply {
            skipUnchangedComposition = true
            skipUnchangedMeasure = true
            retainUnchangedDraw = true
        }
        repeat(warmUp) { frame(host) }
        host.frameStages = this
        sums.fill(0.0)
        collect()
        val before = heapBytes()
        repeat(frames) { frame(host) }
        val total = heapBytes() - before
        host.frameStages = null
        var staged = 0.0
        for (i in 0 until STAGES) staged += sums[i]
        sums[STAGES] = total - staged
        return DoubleArray(STAGES + 1) { sums[it] / frames / BYTES_PER_KB }
    }

    companion object {
        const val PLATES = 200
        private const val BYTES_PER_KB = 1024.0
        private val STAGES = ComposeFrameStage.entries.size

        /** The stages [measure] reports, in its order. */
        val Names: List<String> = ComposeFrameStage.entries.map { it.name.lowercase() } + "rest"

        /** One line naming each stage's KB per frame for one still case. */
        fun line(still: DoubleArray): String = Names.indices.joinToString(", ") { i -> "${Names[i]}=${still[i].tenths()}" }

        /** One line naming each stage's KB per frame, moving and still. */
        fun line(moving: DoubleArray, still: DoubleArray): String = Names.indices.joinToString(", ") { i ->
            "${Names[i]} moving=${moving[i].tenths()} still=${still[i].tenths()}"
        }

        private fun Double.tenths(): String = ((this * 10).toInt() / 10.0).toString()
    }
}
