/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.render.passes

/** Whether the page can collect garbage on demand and read its heap to the byte. */
@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
internal external fun heapMeasurable(): Boolean

/** The JS heap's size in bytes after a full collection. */
@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
internal external fun collectedHeap(): Double

/** The JS heap's size in bytes now. */
@JsFun("() => performance.memory.usedJSHeapSize")
internal external fun heapNow(): Double

/** The median heap growth of [samples] runs of [pass], after [warmup] unmeasured ones, divided by [per]. */
internal fun medianGrowth(per: Int, warmup: Int = 10, samples: Int = 9, pass: () -> Any?): Double {
    repeat(warmup) { pass() }
    val growth = DoubleArray(samples) {
        val before = collectedHeap()
        pass()
        heapNow() - before
    }
    growth.sort()
    return growth[samples / 2] / per
}
