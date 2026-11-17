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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val figures = FollowerParts.Breakdown.associate { (name, parts) ->
            name to (kbAFrame(parts, moving = true) to kbAFrame(parts, moving = false))
        }
        val cases = figures.entries.joinToString(", ") { (name, kb) -> "$name moving=${kb.first.kb()} still=${kb.second.kb()}" }
        println("FollowerHeapBreakdown (JVM): $PLATES plates, KB allocated a skipped frame: $cases; skipped frames that composed or measured: $composedFrames")
        assertEquals(0, composedFrames, "skipped frames that composed or measured")
        // A still frame allocates nothing per element: 358 KB before the semantics tree was reused and
        // placement stopped boxing, 11.6 KB after, the same with a bar or text under each panel.
        val all = figures.getValue("all")
        assertTrue(all.second <= STILL_BUDGET_KB, "a still frame of the whole HUD allocated ${all.second} KB")
        val perKind = figures.getValue("panels+bar").second - figures.getValue("panels").second
        assertTrue(perKind <= PER_KIND_BUDGET_KB, "a bar under each of $PLATES panels added $perKind KB to a still frame")
        // Moving rebuilds the moved semantics nodes and projects each follower: 378 KB before, 102 KB after.
        assertTrue(all.first <= MOVING_BUDGET_KB, "a moving frame of the whole HUD allocated ${all.first} KB")
    }

    private fun Double.kb(): String = ((this * 10).toInt() / 10.0).toString()

    private companion object {
        const val PLATES = 200
        const val WARM_UP = 200
        const val MEASURED = 100
        const val BYTES_PER_KB = 1024.0
        const val STILL_BUDGET_KB = 16.0
        const val PER_KIND_BUDGET_KB = 2.0
        const val MOVING_BUDGET_KB = 128.0
    }
}
