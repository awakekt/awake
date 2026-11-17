/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import kotlin.test.Test

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun collectedHeapBytes(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapBytesNow(): Double

/**
 * Where a skipped [FollowerHud] frame's heap goes in a browser (#1512). Chromium grew the heap by
 * about 840 KB a frame for the 200-plate HUD, against 378 KB allocated on the JVM, so about 460 KB is
 * Wasm-only. Each [FollowerParts.Breakdown] case is a HUD of its own with skipping on, measured with
 * its nodes moving and still, after a full collection; the smaller of two runs is kept. The
 * difference between two cases is what the part they differ by costs a frame.
 * [FollowerHeapBreakdownProbeTest] prints the same cases on the JVM.
 */
class FollowerHeapBreakdownBrowserTest {
    private var composedFrames = 0

    private fun kbAFrame(parts: FollowerParts, moving: Boolean): Double {
        val hud = FollowerHud(PLATES, parts)
        val host = ComposeHost().apply {
            skipUnchangedComposition = true
            skipUnchangedMeasure = true
            retainUnchangedDraw = true
        }
        repeat(WARM_UP) { hud.frame(host, moving) }
        var composed = 0
        val before = collectedHeapBytes()
        repeat(MEASURED) {
            hud.frame(host, moving)
            if (host.frameCounts.composed || host.frameCounts.measured) composed++
        }
        val kb = (heapBytesNow() - before) / MEASURED / BYTES_PER_KB
        composedFrames += composed
        return kb
    }

    @Test
    fun aSkippedFollowerFramesHeapIsNamedPartByPart() {
        val cases = FollowerParts.Breakdown.joinToString(", ") { (name, parts) ->
            val moving = minOf(kbAFrame(parts, moving = true), kbAFrame(parts, moving = true))
            val still = minOf(kbAFrame(parts, moving = false), kbAFrame(parts, moving = false))
            "$name moving=${moving.kb()} still=${still.kb()}"
        }
        println("FollowerHeapBreakdown (browser): $PLATES plates, KB of heap a skipped frame: $cases; skipped frames that composed or measured: $composedFrames")
    }

    private fun Double.kb(): String = ((this * 10).toInt() / 10.0).toString()

    private companion object {
        const val PLATES = 200
        const val WARM_UP = 60
        const val MEASURED = 30
        const val BYTES_PER_KB = 1024.0
    }
}
