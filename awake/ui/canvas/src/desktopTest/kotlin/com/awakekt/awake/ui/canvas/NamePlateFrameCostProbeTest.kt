/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math.Vec3f
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a crowd of name plates costs a frame: planning 100 of them, ranked under a limit, allocates
 * nothing once warm, and the whole canvas frame's time and bytes are printed for the record (#1062).
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class NamePlateFrameCostProbeTest {
    private val plates = List(PLATES) { index ->
        CanvasItem(
            index + 1,
            CanvasElement().apply {
                kind = CanvasElementKind.Text
                text = "Player $index"
                fontSize = 12f
                width = 70f
                height = 16f
                anchor = CanvasAnchor.BottomCenter
                follow = "player-$index"
                followRules = CanvasFollowRules(
                    scale = CanvasCurve(listOf(CanvasCurvePoint(5f, 1.5f), CanvasCurvePoint(60f, 0.6f))),
                    fade = CanvasCurve(listOf(CanvasCurvePoint(40f, 1f), CanvasCurvePoint(70f, 0f))),
                )
            },
        )
    }

    /** Each player somewhere on a 1280 by 720 frame, drifting a little each frame; projections are made once. */
    private class Crowd : CanvasResolver {
        val points = HashMap<String, CanvasProjection>()
        var frame = 0

        fun move() {
            frame++
            for (i in 0 until PLATES) {
                val x = 40f + (i % 20) * 60f + (frame % 10) * 0.05f
                val y = 80f + (i / 20) * 120f
                points["player-$i"] = CanvasProjection(x, y, distance = 5f + i * 0.6f)
            }
        }

        override fun project(target: String, offset: Vec3f): CanvasProjection? = points[target]
    }

    @Test
    fun planningAHundredPlatesAllocatesNothingPerFrame() {
        val crowd = Crowd().apply { move() }
        val tree = CanvasTree(plates, showTouchControls = false, images = emptyMap(), resolver = crowd)
        val limits = listOf(CanvasFollowLimit(max = 60, rankBy = CanvasFollowRank.Distance))
        repeat(WARMUP_FRAMES) { tree.planFollowers(1280f, 720f, 1f, limits) }

        val start = allocated()
        repeat(MEASURED_FRAMES) { tree.planFollowers(1280f, 720f, 1f, limits) }

        assertEquals(0L, (allocated() - start) / MEASURED_FRAMES, "bytes per planned frame")
    }

    @Test
    fun printsTheWholeCanvasFrameForAHundredPlates() {
        val crowd = Crowd()
        val host = ComposeHost()
        fun frame() {
            crowd.move()
            host.frame(FrameInput(1280, 720)) { CanvasOverlay(plates, resolver = crowd) }
        }
        repeat(WARMUP_FRAMES) { frame() }

        // The resolver's projections are the host's, made here per frame; they are counted too.
        val start = allocated()
        val began = System.nanoTime()
        repeat(MEASURED_FRAMES) { frame() }
        val elapsedMs = (System.nanoTime() - began) / 1e6

        println(
            "NamePlateFrameCost: $PLATES plates, ${(allocated() - start) / MEASURED_FRAMES} bytes and " +
                "${"%.3f".format(elapsedMs / MEASURED_FRAMES)} ms per frame over $MEASURED_FRAMES frames (${"%.1f".format(elapsedMs)} ms in all)",
        )
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val PLATES = 100
        const val WARMUP_FRAMES = 200
        const val MEASURED_FRAMES = 300
    }
}
