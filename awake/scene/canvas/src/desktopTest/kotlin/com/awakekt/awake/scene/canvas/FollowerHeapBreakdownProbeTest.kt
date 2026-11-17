/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * [FollowerHeapBreakdownBrowserTest]'s cases on the JVM (#1512): what each [FollowerParts.Breakdown]
 * case allocates a skipped frame, moving and still, so the browser's figures have a JVM line to be
 * read against. A part whose browser figure is well above its JVM one is where the Wasm-only heap is.
 */
class FollowerHeapBreakdownProbeTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    private var composedFrames = 0

    private fun kbAFrame(parts: FollowerParts, moving: Boolean): Double {
        val hud = FollowerHud(PLATES, parts)
        val host = ComposeHost().apply {
            skipUnchangedComposition = true
            skipUnchangedMeasure = true
            retainUnchangedDraw = true
        }
        repeat(WARM_UP) { hud.frame(host, moving) }
        var composed = 0
        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED) {
            hud.frame(host, moving)
            if (host.frameCounts.composed || host.frameCounts.measured) composed++
        }
        val kb = (threadBean.currentThreadAllocatedBytes - before).toDouble() / MEASURED / BYTES_PER_KB
        composedFrames += composed
        return kb
    }

    @Test
    fun aSkippedFollowerFramesAllocationIsNamedPartByPart() {
        val cases = FollowerParts.Breakdown.joinToString(", ") { (name, parts) ->
            "$name moving=${kbAFrame(parts, moving = true).kb()} still=${kbAFrame(parts, moving = false).kb()}"
        }
        println("FollowerHeapBreakdown (JVM): $PLATES plates, KB allocated a skipped frame: $cases; skipped frames that composed or measured: $composedFrames")
    }

    private fun Double.kb(): String = ((this * 10).toInt() / 10.0).toString()

    private companion object {
        const val PLATES = 200
        const val WARM_UP = 200
        const val MEASURED = 100
        const val BYTES_PER_KB = 1024.0
    }
}
