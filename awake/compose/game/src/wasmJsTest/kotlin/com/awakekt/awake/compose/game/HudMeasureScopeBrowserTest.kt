/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.compose.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [HudMeasureScopeTest] in a browser (#1621): the same HUD and the same four cases, each stage read
 * as JS heap growth from a collected heap. The consumer's wasm build measures `ui:measure` largest
 * (842 KB a frame on 0.52.0) where the JVM measures it smallest, so this names the stage, and the
 * bytes per measured node, that wasm adds. Numbers are printed; only what holds on every platform is
 * asserted.
 */
class HudMeasureScopeBrowserTest {
    @Test
    fun aFrameMeasuresTheWholeHudUnlessNothingItReadChanged() {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val scope = HudMeasureScope { heapNow() }
        val readings = HudMeasureScope.Cases.associateWith { (skips, ticking) ->
            scope.read(skips, ticking, warmUp = WARMUP, windows = WINDOWS, frames = FRAMES, collect = { collectedHeap() })
        }
        readings.forEach { (case, reading) -> println("PERF HudMeasureScope wasm skips=${case.first} ticking=${case.second}: $reading") }

        val nodes = readings.getValue(HudMeasureScope.Skips.Off to false).nodesMeasured
        assertTrue(nodes > HudMeasureScope.ROWS * 4, "the HUD has $nodes nodes")
        assertEquals(0, readings.getValue(HudMeasureScope.Skips.All to false).nodesMeasured, "a still frame with skipping on measures nothing")
        assertEquals(nodes, readings.getValue(HudMeasureScope.Skips.DrawOnly to false).nodesMeasured, "with only draw retained, a still frame measures all")
        assertEquals(nodes, readings.getValue(HudMeasureScope.Skips.All to true).nodesMeasured, "one changed value measures the whole tree again")
    }

    private companion object {
        const val WARMUP = 60
        const val WINDOWS = 5
        const val FRAMES = 20
    }
}

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double
