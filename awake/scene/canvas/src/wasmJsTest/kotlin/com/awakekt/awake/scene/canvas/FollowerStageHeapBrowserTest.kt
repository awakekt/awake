/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.scene.canvas

import kotlin.test.Test

@JsFun("() => { globalThis.gc(); }")
private external fun collectGarbage()

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun usedHeapBytes(): Double

/**
 * [FollowerStageHeap] in a browser (#1512): the heap a skipped follower frame grows by in each stage
 * of `ComposeHost.frame`. A still frame allocates about 0.5 KB per element here and none on the JVM,
 * so the stage whose figure stands above [FollowerStageHeapProbeTest]'s holds the Wasm-only part.
 * Each case keeps the smaller of two runs.
 */
class FollowerStageHeapBrowserTest {
    private fun run(moving: Boolean): DoubleArray {
        val first = FollowerStageHeap(::usedHeapBytes).measure(moving, WARM_UP, FRAMES, ::collectGarbage)
        val second = FollowerStageHeap(::usedHeapBytes).measure(moving, WARM_UP, FRAMES, ::collectGarbage)
        return DoubleArray(first.size) { minOf(first[it], second[it]) }
    }

    @Test
    fun aSkippedFollowerFramesHeapIsNamedStageByStage() {
        val line = FollowerStageHeap.line(run(moving = true), run(moving = false))
        println("FollowerStageHeap (browser): ${FollowerStageHeap.PLATES} plates, KB of heap a skipped frame: $line")
    }

    private companion object {
        const val WARM_UP = 60
        const val FRAMES = 30
    }
}
