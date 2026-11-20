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
 * bytes per measured node, that wasm adds. Numbers are printed.
 *
 * A still frame with all three skips only places, within [PLACE_ONLY_CEILING_KB]: Row and Column
 * boxed every `Int` they aligned a child with, 16.6 KB a frame here, 0.1 KB since (#1621).
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
        val placeOnly = readings.getValue(HudMeasureScope.Skips.All to false).measureBytes / BYTES_PER_KB
        assertTrue(placeOnly < PLACE_ONLY_CEILING_KB, "a still frame places the HUD in $placeOnly KB")
    }

    private companion object {
        const val WARMUP = 60
        const val WINDOWS = 5
        const val FRAMES = 20
        const val PLACE_ONLY_CEILING_KB = 2.0
        const val BYTES_PER_KB = 1024.0
    }
}

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double
