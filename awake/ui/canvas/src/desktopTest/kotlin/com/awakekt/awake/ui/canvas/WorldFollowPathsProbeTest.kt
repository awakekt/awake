/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.AnchorProvider
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.WorldAnchorLayout
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.worldAnchor
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test

/**
 * The same name plate (a frame, a bar and a line of text), moving every frame, once through the canvas
 * follow path and once through `WorldAnchorLayout`, with the time and bytes of each per frame (#1499).
 * Projections are made before the clock starts and cycled, so neither path counts the harness.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent. It prints; it asserts
 * nothing, because the numbers belong to the machine.
 */
class WorldFollowPathsProbeTest {
    /** [CYCLE] frames of projections for [count] anchors, each drifting a pixel or so a frame. */
    private class Crowd(val count: Int) : CanvasResolver {
        private val table = Array(CYCLE) { f -> Array(count) { i -> CanvasProjection(x(i, f), y(i), distance = 5f + i * 0.1f) } }
        var frame = 0
        fun x(i: Int, f: Int) = 40f + (i % 25) * 48f + f * 0.7f
        fun y(i: Int) = 60f + (i / 25) * 30f
        fun at(i: Int): CanvasProjection = table[frame % CYCLE][i]
        override fun project(target: String, offset: Vec3f): CanvasProjection? = at(target.substring(1).toInt())
    }

    private fun canvasItems(count: Int): List<CanvasItem> = buildList {
        for (i in 0 until count) {
            val plate = CanvasElement().apply {
                kind = CanvasElementKind.Panel; width = PLATE_W; height = PLATE_H
                anchor = CanvasAnchor.BottomCenter; follow = "p$i"
            }
            add(CanvasItem(i * 3 + 1, plate))
            add(CanvasItem(i * 3 + 2, CanvasElement().apply { kind = CanvasElementKind.Bar; value = 0.6f; width = 60f; height = 4f }, plate))
            add(CanvasItem(i * 3 + 3, CanvasElement().apply { kind = CanvasElementKind.Text; text = "Player $i"; fontSize = 12f; width = 60f; height = 14f }, plate))
        }
    }

    private class Sample(val ms: Double, val bytes: Long)

    private inline fun run(frame: () -> Unit): Sample {
        repeat(WARMUP) { frame() }
        val start = allocated()
        val began = System.nanoTime()
        repeat(MEASURED) { frame() }
        val ms = (System.nanoTime() - began) / 1e6
        return Sample(ms / MEASURED, (allocated() - start) / MEASURED)
    }

    private fun canvasRun(count: Int): Sample {
        val crowd = Crowd(count)
        val items = canvasItems(count)
        val host = ComposeHost()
        return run {
            crowd.frame++
            host.frame(FrameInput(WIDTH, HEIGHT)) { CanvasOverlay(items, resolver = crowd) }
        }
    }

    private fun composeRun(count: Int): Sample {
        val crowd = Crowd(count)
        val providers = List(count) { i ->
            AnchorProvider { into ->
                val p = crowd.at(i)
                into.x = p.x
                into.y = p.y
            }
        }
        val host = ComposeHost()
        return run {
            crowd.frame++
            host.frame(FrameInput(WIDTH, HEIGHT)) {
                WorldAnchorLayout(Modifier.fillMaxSize()) {
                    for (i in 0 until count) {
                        Box(Modifier.worldAnchor(providers[i]).size(PLATE_W.dp, PLATE_H.dp).background(Color(0.1f, 0.1f, 0.1f))) {
                            Box(Modifier.size(36.dp, 4.dp).background(Color(0.2f, 0.8f, 0.2f)))
                            Text("Player $i")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun printsBothPathsAtEachCount() {
        println("WorldFollowPaths: cpu=${Runtime.getRuntime().availableProcessors()} cores, ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, runs=$RUNS")
        for (count in COUNTS) {
            for ((name, path) in listOf<Pair<String, (Int) -> Sample>>("canvas" to ::canvasRun, "compose" to ::composeRun)) {
                val samples = List(RUNS) { path(count) }
                val ms = samples.map { it.ms }
                val bytes = samples.map { it.bytes }
                println(
                    "WorldFollowPaths: $name n=$count ms/frame mean=${"%.3f".format(ms.average())} min=${"%.3f".format(ms.min())} " +
                        "max=${"%.3f".format(ms.max())} bytes/frame min=${bytes.min()} max=${bytes.max()} (${ms.joinToString { "%.3f".format(it) }})",
                )
            }
        }
    }

    /** Attribution: [variant] 0 = anchors around empty boxes, 1 = same content with no anchor layout (plain Box). */
    private fun variantRun(count: Int, variant: Int): Sample {
        val crowd = Crowd(count)
        val providers = List(count) { i -> AnchorProvider { into -> val p = crowd.at(i); into.x = p.x; into.y = p.y } }
        val host = ComposeHost()
        return run {
            crowd.frame++
            host.frame(FrameInput(WIDTH, HEIGHT)) {
                if (variant == 0) {
                    WorldAnchorLayout(Modifier.fillMaxSize()) {
                        for (i in 0 until count) Box(Modifier.worldAnchor(providers[i]).size(PLATE_W.dp, PLATE_H.dp))
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        for (i in 0 until count) {
                            Box(Modifier.size(PLATE_W.dp, PLATE_H.dp).background(Color(0.1f, 0.1f, 0.1f))) {
                                Box(Modifier.size(36.dp, 4.dp).background(Color(0.2f, 0.8f, 0.2f)))
                                Text("Player $i")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun printsAttribution() {
        for (v in 0..1) {
            val samples = List(3) { variantRun(200, v) }
            println("WorldFollowAttribution: variant=$v n=200 ms=${samples.map { "%.3f".format(it.ms) }} bytes=${samples.map { it.bytes }}")
        }
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val PLATE_W = 70f
        const val PLATE_H = 20f
        const val CYCLE = 64
        const val WARMUP = 300
        const val MEASURED = 400
        const val RUNS = 5
        val COUNTS = listOf(50, 200, 500)
    }
}
