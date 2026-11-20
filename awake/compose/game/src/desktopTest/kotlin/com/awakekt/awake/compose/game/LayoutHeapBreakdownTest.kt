/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [LayoutHeapBreakdown] on the JVM (#1621): what each piece of the HUD allocates measuring and
 * placing. [LayoutHeapBreakdownBrowserTest] reads the same in a browser, where a still HUD frame's
 * placement alone allocates 16.6 KB and the JVM's none, so the pieces that differ are the wasm-only
 * allocators. Numbers are printed; only the node counts are asserted.
 */
class LayoutHeapBreakdownTest {
    @Test
    fun eachPieceOfTheHudNamesWhatItsLayoutAllocates() {
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val breakdown = LayoutHeapBreakdown { threads.currentThreadAllocatedBytes.toDouble() }
        LayoutHeapBreakdown.Part.entries.forEach { part ->
            val reading = breakdown.read(part, warmUp = WARMUP, windows = WINDOWS, frames = FRAMES, collect = {})
            println("LayoutHeapBreakdown jvm $part: $reading")
            assertTrue(reading.nodes >= LayoutHeapBreakdown.ROWS, "$part lays out ${reading.nodes} nodes")
        }
    }

    private companion object {
        const val WARMUP = 300
        const val WINDOWS = 5
        const val FRAMES = 100
    }
}
