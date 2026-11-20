/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.compose.game

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [LayoutHeapBreakdownTest] in a browser (#1621): each piece of the HUD's layout heap, measuring and
 * placing, read as JS heap growth from a collected heap. A piece whose bytes a node stand above the
 * JVM's names a wasm-only allocator. Numbers are printed.
 *
 * Rows place within [ROW_PLACE_CEILING] bytes a node and measure within [ROW_MEASURE_CEILING]: their
 * cross-axis alignment and arrangement went through SAM-converted references, which boxed every
 * `Int` per child, 105 B a node placing and 52 B measuring; 1.2 B and 31 B since (#1621).
 */
class LayoutHeapBreakdownBrowserTest {
    @Test
    fun eachPieceOfTheHudNamesWhatItsLayoutAllocates() {
        assertTrue(layoutHeapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val breakdown = LayoutHeapBreakdown { layoutHeapNow() }
        val readings = LayoutHeapBreakdown.Part.entries.associateWith { part ->
            breakdown.read(part, warmUp = WARMUP, windows = WINDOWS, frames = FRAMES, collect = { layoutCollectedHeap() })
        }
        readings.forEach { (part, reading) ->
            println("PERF LayoutHeapBreakdown wasm $part: $reading")
            assertTrue(reading.nodes >= LayoutHeapBreakdown.ROWS, "$part lays out ${reading.nodes} nodes")
        }
        val rows = readings.getValue(LayoutHeapBreakdown.Part.SpacedRows)
        assertTrue(rows.placeBytes / rows.nodes < ROW_PLACE_CEILING, "rows place in ${rows.placeBytes / rows.nodes} B a node")
        assertTrue(rows.measureBytes / rows.nodes < ROW_MEASURE_CEILING, "rows measure in ${rows.measureBytes / rows.nodes} B a node")
    }

    private companion object {
        const val WARMUP = 60
        const val WINDOWS = 5
        const val FRAMES = 20
        const val ROW_PLACE_CEILING = 20.0
        const val ROW_MEASURE_CEILING = 42.0
    }
}

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun layoutHeapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun layoutCollectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun layoutHeapNow(): Double
