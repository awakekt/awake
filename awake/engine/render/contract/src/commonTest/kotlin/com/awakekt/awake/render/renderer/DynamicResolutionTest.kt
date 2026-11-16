/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dynamic resolution for Auto (#1688), over simulated frames: a 2400 by 1080 phone panel whose GPU
 * cost follows the pixels drawn, measured by GPU time where the device times it and by frame time
 * where it does not, with and without a display that caps the rate below the target.
 */
class DynamicResolutionTest {
    private val panel = 1080
    private val target = DynamicResolution(targetFps = 60, floorShortEdge = 540)

    /** Milliseconds of GPU work at [edge]: [atNative] at the full panel, scaling with the pixels. */
    private fun gpuMs(edge: Float, atNative: Float) = atNative * (edge / panel) * (edge / panel)

    @Test
    fun aHeavyGpuFrameDropsToTheEdgeThatFitsAndStaysThere() {
        val controller = DynamicResolutionController(target, ceilingShortEdge = panel)
        val lagged = ArrayDeque(List(3) { 30f })
        val edges = FloatArray(900)
        repeat(900) { frame ->
            lagged.addLast(gpuMs(controller.shortEdge, atNative = 30f))
            controller.update(frameSeconds = max(lagged.first(), 16.7f) / 1000f, gpuTimeMs = lagged.removeFirst(), panel)
            edges[frame] = controller.shortEdge
        }
        val fits = panel * sqrt(target.frameBudgetMs * 0.85f / 30f)
        assertTrue(abs(controller.shortEdge - fits) / fits < 0.1f, "settled at ${controller.shortEdge}, fits at $fits")
        assertTrue(edges.copyOfRange(300, 900).distinct().size <= 2, "it holds once settled: ${edges.copyOfRange(300, 900).distinct()}")
    }

    @Test
    fun aLightGpuFrameNeverMoves() {
        val controller = DynamicResolutionController(target, ceilingShortEdge = panel)
        repeat(600) { controller.update(0.0167f, gpuTimeMs = 6f, panel) }
        assertEquals(panel.toFloat(), controller.shortEdge)
        assertEquals(RenderScale.FULL, controller.scaleFor(panel))
    }

    /** A 30 Hz display under a 60 fps target: every frame takes 33 ms, but the GPU has room. */
    @Test
    fun withGpuTimeADisplayCappedBelowTheTargetLeavesTheResolutionAlone() {
        val controller = DynamicResolutionController(target, ceilingShortEdge = panel)
        repeat(600) { controller.update(0.0333f, gpuTimeMs = 9f, panel) }
        assertEquals(panel.toFloat(), controller.shortEdge)
    }

    @Test
    fun withoutGpuTimeMissedFramesStepDownUntilFramesFit() {
        val controller = DynamicResolutionController(target, ceilingShortEdge = panel)
        repeat(1_200) { controller.update(gpuMs(controller.shortEdge, atNative = 28f) / 1000f, gpuTimeMs = null, panel) }
        val frameMs = gpuMs(controller.shortEdge, atNative = 28f)
        assertTrue(frameMs <= target.frameBudgetMs * (1f + MissedFrameScaler.MISS_TOLERANCE), "frames at $frameMs ms")
        assertTrue(controller.shortEdge > 700f, "no lower than it needs: ${controller.shortEdge}")
    }

    @Test
    fun withoutGpuTimeFailedProbesBackOffSoTheEdgeDoesNotSeeSaw() {
        val controller = DynamicResolutionController(target, ceilingShortEdge = panel)
        var rises = 0
        var previous = controller.shortEdge
        repeat(12_000) { frame ->
            controller.update(gpuMs(controller.shortEdge, atNative = 28f) / 1000f, gpuTimeMs = null, panel)
            if (frame >= 2_400 && controller.shortEdge > previous) rises++
            previous = controller.shortEdge
        }
        // 9,600 frames is 320 windows; probing every 4 windows would rise 80 times.
        assertTrue(rises <= 12, "probes after settling: $rises")
    }

    /** A 30 Hz display under a 60 fps target, and no GPU timing: every frame reads 33 ms whatever is drawn. */
    @Test
    fun withoutGpuTimeADisplayCappedBelowTheTargetGivesBackItsSteps() {
        val controller = DynamicResolutionController(target, ceilingShortEdge = panel)
        var lowest = controller.shortEdge
        repeat(1_800) {
            controller.update(0.0333f, gpuTimeMs = null, panel)
            lowest = minOf(lowest, controller.shortEdge)
        }
        assertEquals(panel.toFloat(), controller.shortEdge, "back at the ceiling")
        assertTrue(lowest >= panel * 0.8f, "at most the two steps it took to find out: $lowest")
    }

    @Test
    fun theFloorHoldsAndASmallSurfaceRendersFull() {
        val controller = DynamicResolutionController(target, ceilingShortEdge = panel)
        repeat(1_200) { controller.update(0.05f, gpuTimeMs = 80f, panel) }
        assertEquals(540f, controller.shortEdge)
        assertEquals(0.5f, controller.scaleFor(panel))
        assertEquals(RenderScale.FULL, controller.scaleFor(480), "never upsampled")
    }
}
