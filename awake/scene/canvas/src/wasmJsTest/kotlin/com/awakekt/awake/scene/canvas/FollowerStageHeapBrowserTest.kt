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
    private fun smaller(run: (FollowerStageHeap) -> DoubleArray): DoubleArray {
        val first = run(FollowerStageHeap(::usedHeapBytes))
        val second = run(FollowerStageHeap(::usedHeapBytes))
        return DoubleArray(first.size) { minOf(first[it], second[it]) }
    }

    private fun run(moving: Boolean, parts: FollowerParts = FollowerParts.All) =
        smaller { it.measure(moving, WARM_UP, FRAMES, ::collectGarbage, parts) }

    @Test
    fun aSkippedFollowerFramesHeapIsNamedStageByStage() {
        val line = FollowerStageHeap.line(run(moving = true), run(moving = false))
        println("FollowerStageHeap (browser): ${FollowerStageHeap.PLATES} plates, KB of heap a skipped frame: $line")
        // Still, without following, and with no canvas at all: is the Wasm-only heap the canvas's
        // follower placement, or core placement (#1512)?
        val unfollowed = run(moving = false, FollowerParts.All.copy(follow = false))
        val boxes = PlainOffsetBoxes(PLAIN_BOXES)
        val plain = smaller { it.measure(WARM_UP, FRAMES, ::collectGarbage) { host -> boxes.frame(host) } }
        val perLink = PlainOffsetBoxes.Links.entries.joinToString("; ") { links ->
            val kind = PlainOffsetBoxes(PLAIN_BOXES, links)
            val placement = smaller { it.measure(WARM_UP, FRAMES, ::collectGarbage) { host -> kind.frame(host) } }[1]
            "$links ${((placement * 10).toInt() / 10.0)}"
        }
        println("FollowerStageHeap (browser), still, $PLAIN_BOXES plain boxes, KB of heap in placement by link: $perLink")
        val raw = listOf("sized" to PlainLayouts(PLAIN_BOXES, leaves = false), "leaves" to PlainLayouts(PLAIN_BOXES, leaves = true))
            .joinToString("; ") { (name, kind) ->
                val placement = smaller { it.measure(WARM_UP, FRAMES, ::collectGarbage) { host -> kind.frame(host) } }[1]
                "$name ${((placement * 10).toInt() / 10.0)}"
            }
        println("FollowerStageHeap (browser), still, $PLAIN_BOXES children of a parent that places them itself, KB in placement: $raw")
        println(
            "FollowerStageHeap (browser), still: plates not following ${FollowerStageHeap.line(unfollowed)}; " +
                "$PLAIN_BOXES plain offset boxes ${FollowerStageHeap.line(plain)}",
        )
    }

    private companion object {
        const val WARM_UP = 60
        const val FRAMES = 30
        const val PLAIN_BOXES = 600
    }
}
