/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.compose.state

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double

/** [SelectedPollAllocationTest] in the browser: 100 selected parts checked without growing the heap (#1512). */
class SelectedPollBrowserTest {
    @Test
    fun aStillFlowIsCheckedWithoutGrowingTheHeap() {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val workload = SelectedPollWorkload(PARTS)
        repeat(WARMUP) { assertFalse(workload.poll()) }
        val growth = DoubleArray(SAMPLES) {
            val before = collectedHeap()
            repeat(POLLS) { workload.poll() }
            (heapNow() - before) / POLLS
        }.sorted()
        val perCheck = growth[SAMPLES / 2]
        println("SelectedPoll (browser): $PARTS parts, $perCheck B a check")
        assertTrue(perCheck <= BUDGET, "a check of $PARTS parts grew the heap by $perCheck B")
    }

    private companion object {
        const val PARTS = 100
        const val WARMUP = 200
        const val POLLS = 200
        const val SAMPLES = 9

        /** Bytes a check: the heap's own noise, well under one object a check. */
        const val BUDGET = 2.0
    }
}
