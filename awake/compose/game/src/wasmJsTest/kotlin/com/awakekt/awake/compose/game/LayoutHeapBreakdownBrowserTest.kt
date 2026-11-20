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
 * JVM's names a wasm-only allocator. Numbers are printed; only the node counts are asserted.
 */
class LayoutHeapBreakdownBrowserTest {
    @Test
    fun eachPieceOfTheHudNamesWhatItsLayoutAllocates() {
        assertTrue(layoutHeapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val breakdown = LayoutHeapBreakdown { layoutHeapNow() }
        LayoutHeapBreakdown.Part.entries.forEach { part ->
            val reading = breakdown.read(part, warmUp = WARMUP, windows = WINDOWS, frames = FRAMES, collect = { layoutCollectedHeap() })
            println("PERF LayoutHeapBreakdown wasm $part: $reading")
            assertTrue(reading.nodes >= LayoutHeapBreakdown.ROWS, "$part lays out ${reading.nodes} nodes")
        }
    }

    private companion object {
        const val WARMUP = 60
        const val WINDOWS = 5
        const val FRAMES = 20
    }
}

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun layoutHeapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun layoutCollectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun layoutHeapNow(): Double
