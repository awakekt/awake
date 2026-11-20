/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A frame's GPU split and waits reach [FrameBreakdown]'s summaries under their own keys (#1709). */
class FrameGpuSampleTest {
    @Test
    fun eachGpuPartAndWaitIsSummarisedUnderItsKey() {
        val gpu = FrameGpuSample(
            shadowDepthMs = 1f, sceneDepthMs = 2f, maskMs = 0f, sceneMs = 9f, postMs = 1.5f, upscaleMs = 3f, uiMs = 4f,
            frameResourceWaitMs = 30f, acquireWaitMs = 0.5f,
        )
        val breakdown = FrameBreakdown(sample = { FrameBreakdownSample(gpu = gpu) })
        repeat(10) { breakdown.frame(nowSeconds = it * 0.04, durationSeconds = 0.04) }

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 0.4, windowSeconds = 30.0))
        assertEquals(1.0, summaries.getValue(FrameBreakdown.KEY_GPU_SHADOW_DEPTH_MS).avg)
        assertEquals(2.0, summaries.getValue(FrameBreakdown.KEY_GPU_SCENE_DEPTH_MS).avg)
        assertEquals(0.0, summaries.getValue(FrameBreakdown.KEY_GPU_MASK_MS).avg)
        assertEquals(9.0, summaries.getValue(FrameBreakdown.KEY_GPU_SCENE_MS).avg)
        assertEquals(1.5, summaries.getValue(FrameBreakdown.KEY_GPU_POST_MS).avg)
        assertEquals(3.0, summaries.getValue(FrameBreakdown.KEY_GPU_UPSCALE_MS).avg)
        assertEquals(4.0, summaries.getValue(FrameBreakdown.KEY_GPU_UI_MS).avg)
        assertEquals(30.0, summaries.getValue(FrameBreakdown.KEY_FRAME_RESOURCE_WAIT_MS).avg)
        assertEquals(0.5, summaries.getValue(FrameBreakdown.KEY_ACQUIRE_WAIT_MS).avg)
    }

    @Test
    fun aPartTheDeviceHasNotReportedHasNoSeries() {
        val breakdown = FrameBreakdown(sample = { FrameBreakdownSample(gpu = FrameGpuSample(frameResourceWaitMs = 2f)) })
        repeat(3) { breakdown.frame(nowSeconds = it * 0.04, durationSeconds = 0.04) }

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 0.2, windowSeconds = 30.0))
        assertNull(summaries[FrameBreakdown.KEY_GPU_SCENE_MS])
        assertEquals(2.0, summaries.getValue(FrameBreakdown.KEY_FRAME_RESOURCE_WAIT_MS).avg)
    }
}
