/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.scene.canvas

import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double

@JsFun("() => performance.now()")
private external fun nowMillis(): Double

/**
 * What the check [SceneCanvas] gives a host that skips unchanged frames costs in a browser (#1512),
 * at game-scale element counts: the time of one check, printed, and the heap it grows, at most
 * [BUDGET] bytes a check on a still canvas, where an object a check would be 16 or more.
 */
class SceneCanvasPollBrowserTest {
    @Test
    fun aStillCanvasIsCheckedWithoutGrowingTheHeap() {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val lines = SIZES.map { size ->
            val workload = SceneCanvasPollWorkload(size)
            val first = workload.read()
            repeat(WARMUP) { assertSame(first, workload.read(), "a still canvas answered a change") }
            val micros = DoubleArray(SAMPLES) {
                val start = nowMillis()
                repeat(READS) { workload.read() }
                (nowMillis() - start) * MICROS_PER_MILLI / READS
            }.sorted()
            val growth = DoubleArray(SAMPLES) {
                val before = collectedHeap()
                repeat(READS) { workload.read() }
                (heapNow() - before) / READS
            }.sorted()
            val perCheck = growth[SAMPLES / 2]
            assertTrue(perCheck <= BUDGET, "a check of $size elements grew the heap by $perCheck B")
            "$size elements ${micros[SAMPLES / 2]} us, $perCheck B"
        }
        println("SceneCanvasPoll (browser): ${lines.joinToString("; ")}")
    }

    private companion object {
        val SIZES = intArrayOf(300, 1_000, 3_000).toList()
        const val WARMUP = 200
        const val READS = 200
        const val SAMPLES = 9
        const val MICROS_PER_MILLI = 1_000.0

        /** Bytes a check: the heap's own noise, well under one object a check. */
        const val BUDGET = 2.0
    }
}
