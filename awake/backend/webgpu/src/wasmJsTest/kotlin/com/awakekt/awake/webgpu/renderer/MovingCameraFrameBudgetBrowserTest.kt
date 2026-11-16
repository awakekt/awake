/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * What a warm frame costs in the browser with the camera moving over a field of static props, the
 * frame a consumer's web build pays most of the time: GPU buffers created and wasm heap allocated
 * (#1621). Budgets, so a regression fails here instead of in a consumer's profile after a release.
 *
 * [MovingField]'s props, shadowed, the camera sweeping a loop. Heap is the median of
 * per-frame growth of the JS heap (where Kotlin/Wasm's objects live), each frame measured from a
 * collected heap so a collection inside the window is rare and the median drops it.
 */
class MovingCameraFrameBudgetBrowserTest {
    private companion object {
        const val WARM_UP = MovingField.WARM_UP
        const val MEASURED = MovingField.MEASURED
        const val KB = MovingField.KB

        /**
         * Just above what a warm frame allocates now, 600 KB measured (Chromium, lavapipe): 705 KB
         * before #1555's packet fixes fails it. Most of what is left is not per-draw; see #1621.
         */
        const val HEAP_BUDGET_KB = 640
    }

    @Test
    fun aWarmMovingFrameStaysWithinItsBudgets() = runTest(timeout = 4.minutes) {
        assertTrue(heapMeasurable(), "needs the launcher's --js-flags=--expose-gc and --enable-precise-memory-info")
        val engine = BundleBrowserEngine(1)
        try {
            val renderer = engine.boot()
            val scene = MovingField(renderer)
            repeat(WARM_UP) { scene.frame(it) }
            countCreateBuffer(true)
            val grown = DoubleArray(MEASURED) { index ->
                val before = collectedHeap()
                scene.frame(WARM_UP + index)
                heapNow() - before
            }
            val created = createBufferCount()
            countCreateBuffer(false)
            grown.sort()
            val perFrame = grown[MEASURED / 2]
            println("MovingCameraFrameBudget: heap ${(perFrame / KB).toInt()} KB a frame (median), $created buffers over $MEASURED frames")

            assertEquals(0, created, "GPU buffers created over $MEASURED warm moving frames")
            assertTrue(perFrame <= HEAP_BUDGET_KB * KB, "a warm moving frame allocated ${(perFrame / KB).toInt()} KB, over $HEAP_BUDGET_KB KB")
            assertEquals(emptyList(), engine.errors, "WebGPU validation errors")
        } finally {
            countCreateBuffer(false)
            engine.destroy()
        }
    }
}

/** Starts or stops counting `GPUDevice.prototype.createBuffer` calls, wrapping it once per page. */
@JsFun(
    """(on) => {
    const proto = GPUDevice.prototype;
    if (!proto.__awakeBudgetCreateBuffer) {
        proto.__awakeBudgetCreateBuffer = proto.createBuffer;
        proto.createBuffer = function (descriptor) {
            if (globalThis.__awakeBudgetCounting) globalThis.__awakeBudgetCreated++;
            return proto.__awakeBudgetCreateBuffer.call(this, descriptor);
        };
    }
    globalThis.__awakeBudgetCounting = on;
    if (on) globalThis.__awakeBudgetCreated = 0;
}""",
)
private external fun countCreateBuffer(on: Boolean)

@JsFun("() => globalThis.__awakeBudgetCreated || 0")
private external fun createBufferCount(): Int
