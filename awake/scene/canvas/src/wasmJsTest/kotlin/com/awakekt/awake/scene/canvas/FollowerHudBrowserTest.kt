/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import kotlin.test.Test
import kotlin.test.assertEquals

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun heapAfterCollecting(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNowBytes(): Double

@JsFun("() => performance.now()")
private external fun nowMs(): Double

/**
 * [FollowerHud] in a browser (#1512): what a frame costs and grows the heap by, composing every frame
 * and skipping unchanged frames, with the nodes moving. Printed; with skipping on, a frame whose
 * nodes only move composes and measures nothing, which this asserts.
 */
class FollowerHudBrowserTest {
    @Test
    fun aFollowerHudsFrameIsMeasuredInTheBrowser() {
        val lines = listOf(false, true).map { skip ->
            val hud = FollowerHud(PLATES)
            val host = ComposeHost().apply {
                skipUnchangedComposition = skip
                skipUnchangedMeasure = skip
                retainUnchangedDraw = skip
            }
            repeat(WARM_UP) { hud.frame(host, moving = true) }
            var composed = 0
            val before = heapAfterCollecting()
            val start = nowMs()
            repeat(MEASURED) {
                hud.frame(host, moving = true)
                if (host.frameCounts.composed || host.frameCounts.measured) composed++
            }
            val ms = (nowMs() - start) / MEASURED
            val kb = (heapNowBytes() - before) / MEASURED / BYTES_PER_KB
            if (skip) assertEquals(0, composed, "with skipping on, frames that composed or measured")
            "skip=$skip: $ms ms, $kb KB of heap a frame, composed or measured $composed of $MEASURED"
        }
        println("FollowerHudFrame (browser): $PLATES plates moving; ${lines.joinToString("; ")}")
    }

    private companion object {
        const val PLATES = 200
        const val WARM_UP = 60
        const val MEASURED = 60
        const val BYTES_PER_KB = 1024.0
    }
}
