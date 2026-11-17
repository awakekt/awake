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
import kotlin.test.assertTrue

@JsFun("() => { globalThis.gc(); return performance.memory.usedJSHeapSize; }")
private external fun heapAfterCollecting(): Double

@JsFun("() => performance.memory.usedJSHeapSize")
private external fun heapNowBytes(): Double

@JsFun("() => performance.now()")
private external fun nowMs(): Double

/**
 * [FollowerHud] in a browser (#1512): what a frame costs and allocates, composing every frame and
 * skipping unchanged frames, with the nodes moving. With skipping on, a frame whose nodes only move
 * composes and measures nothing, and allocates no more than one that composes, which this asserts.
 *
 * Each mode runs in a HUD of its own after a full collection, twice and alternating, skipping first,
 * and keeps its smaller figure. The launcher's 128 MB young generation holds a whole window, so the
 * heap's growth is what the frames allocated: with the default young generation, the mode that
 * allocates more sets off more scavenges inside its window and reads as growing the heap less.
 */
class FollowerHudBrowserTest {
    private class Run(val ms: Double, val kb: Double, val composed: Int)

    private fun run(skip: Boolean): Run {
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
        return Run(ms, kb, composed)
    }

    @Test
    fun aFollowerHudsFrameIsMeasuredInTheBrowser() {
        val runs = listOf(true, false, true, false).map { skip -> skip to run(skip) }
        val skipping = runs.filter { it.first }.map { it.second }
        val composing = runs.filter { !it.first }.map { it.second }
        val lines = runs.joinToString("; ") { (skip, r) ->
            "skip=$skip: ${r.ms} ms, ${r.kb} KB of heap a frame, composed or measured ${r.composed} of $MEASURED"
        }
        println("FollowerHudFrame (browser): $PLATES plates moving; $lines")
        skipping.forEach { assertEquals(0, it.composed, "with skipping on, frames that composed or measured") }
        val skipKb = skipping.minOf { it.kb }
        val composeKb = composing.minOf { it.kb }
        assertTrue(skipKb <= composeKb, "skipping allocated $skipKb KB a frame, more than composing's $composeKb KB")
    }

    private companion object {
        const val PLATES = 200
        const val WARM_UP = 60
        const val MEASURED = 30
        const val BYTES_PER_KB = 1024.0
    }
}
