/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How much of a HUD a frame measures, and what each stage costs on the JVM (#1621, the `ui:measure`
 * line). [HudMeasureScopeBrowserTest] reads the same in a browser.
 *
 * The consumer's `ui:measure` is 842 KB a frame on 0.52.0. Measuring is all or nothing per frame: a
 * host with [com.awakekt.awake.compose.ui.platform.ComposeHost.skipUnchangedMeasure] off measures
 * every node every frame (all three of the host's skips are opt-in), and one with it on skips a frame
 * only when nothing measuring read changed, so one changed value measures the whole tree again. This
 * module's test task turns escape analysis off, so what wasm pays is counted. Numbers are printed;
 * only what holds today is asserted, so they are a baseline, not a budget.
 */
class HudMeasureScopeTest {
    @Test
    fun aFrameMeasuresTheWholeHudUnlessNothingItReadChanged() {
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val scope = HudMeasureScope { threads.currentThreadAllocatedBytes.toDouble() }
        val readings = HudMeasureScope.Cases.associateWith { (skipping, ticking) ->
            scope.read(skipping, ticking, warmUp = WARMUP, windows = WINDOWS, frames = FRAMES, collect = {})
        }
        readings.forEach { (case, reading) -> println("HudMeasureScope jvm skipping=${case.first} ticking=${case.second}: $reading") }

        val nodes = readings.getValue(false to false).nodesMeasured
        assertTrue(nodes > HudMeasureScope.ROWS * 4, "the HUD has $nodes nodes")
        assertEquals(0, readings.getValue(true to false).nodesMeasured, "a still frame with skipping on measures nothing")
        assertEquals(nodes, readings.getValue(true to true).nodesMeasured, "one changed value measures the whole tree again")
        assertEquals(nodes, readings.getValue(false to true).nodesMeasured)
    }

    private companion object {
        const val WARMUP = 300
        const val WINDOWS = 5
        const val FRAMES = 100
    }
}
