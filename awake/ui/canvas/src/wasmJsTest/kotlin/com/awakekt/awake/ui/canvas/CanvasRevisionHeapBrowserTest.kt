/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.ui.canvas

import kotlin.test.Test
import kotlin.test.assertTrue

/** Whether the page can collect garbage on demand and read its heap to the byte. */
@JsFun("() => typeof globalThis.gc === 'function' && !!(performance.memory)")
private external fun heapMeasurable(): Boolean

/** The JS heap's size in bytes after a full collection. */
@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeap(): Double

/** The JS heap's size in bytes now. */
@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNow(): Double

/** Milliseconds from a high-resolution clock. */
@JsFun("() => performance.now()")
private external fun nowMillis(): Double

/**
 * Counting changes costs a HUD nothing in a browser either (#1512): frames of
 * [CanvasRevisionWorkload], writes changed and unchanged and every element's revision read, grow
 * the Kotlin/Wasm heap by no more than [BUDGET] bytes an element a frame, where one boxed Float or
 * Int an element would be 16. Also prints what polling every element's revision takes, the O(n)
 * check a skipping host makes each frame.
 */
class CanvasRevisionHeapBrowserTest {
    @Test
    fun writingAndPollingRevisionsGrowsNoHeap() {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val workload = CanvasRevisionWorkload()
        var frame = 0
        repeat(WARMUP_FRAMES) { workload.frame(frame++) }
        val growth = DoubleArray(SAMPLES) {
            val before = collectedHeap()
            repeat(FRAMES_PER_SAMPLE) { workload.frame(frame++) }
            heapNow() - before
        }
        growth.sort()
        val perElementFrame = growth[SAMPLES / 2] / (FRAMES_PER_SAMPLE * CanvasRevisionWorkload.ELEMENTS)
        // Each half on its own, so a failure names the half that allocates.
        val writes = perElementFrame { workload.writes(frame++) }
        val reads = perElementFrame { workload.reads() }
        println(
            "CanvasRevisionHeap: ${growth.joinToString { it.toInt().toString() }} bytes per $FRAMES_PER_SAMPLE frames; " +
                "median $perElementFrame B an element a frame (writes $writes, reads $reads)",
        )
        assertTrue(perElementFrame <= BUDGET, "a frame grew the heap by $perElementFrame B an element")
    }

    /** The median heap growth of [FRAMES_PER_SAMPLE] runs of [pass], in bytes an element. */
    private fun perElementFrame(pass: () -> Unit): Double {
        val growth = DoubleArray(SAMPLES) {
            val before = collectedHeap()
            repeat(FRAMES_PER_SAMPLE) { pass() }
            heapNow() - before
        }
        growth.sort()
        return growth[SAMPLES / 2] / (FRAMES_PER_SAMPLE * CanvasRevisionWorkload.ELEMENTS)
    }

    @Test
    fun pollingEveryElementsRevisionIsTimed() {
        val elements = CanvasRevisionWorkload().elements
        var sum = 0
        repeat(WARMUP_FRAMES) { for (element in elements) sum += element.contentRevision }
        val samples = DoubleArray(SAMPLES) {
            val start = nowMillis()
            repeat(FRAMES_PER_SAMPLE) { for (element in elements) sum += element.contentRevision }
            (nowMillis() - start) * MICROS_PER_MILLI / FRAMES_PER_SAMPLE
        }
        samples.sort()
        println("CanvasRevisionPoll: ${samples[SAMPLES / 2]} us a frame for ${elements.size} elements (sum $sum)")
    }

    private companion object {
        const val WARMUP_FRAMES = 200
        const val FRAMES_PER_SAMPLE = 50
        const val SAMPLES = 9
        const val MICROS_PER_MILLI = 1_000.0

        /** Bytes an element a frame: the heap's own noise, well under one boxed value (16 B) an element. */
        const val BUDGET = 1.0
    }
}
