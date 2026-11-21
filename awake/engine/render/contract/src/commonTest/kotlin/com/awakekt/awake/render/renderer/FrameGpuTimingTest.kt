/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [FrameGpuTiming.upscaleAndUiMs]: the two parts a scaled Vulkan frame draws in one render pass (#1709). */
class FrameGpuTimingTest {
    private fun timing(upscale: Float, ui: Float) = FrameGpuTiming(
        partMs = FloatArray(GpuFramePart.entries.size) { 1f }.also {
            it[GpuFramePart.Upscale.ordinal] = upscale
            it[GpuFramePart.Ui.ordinal] = ui
        },
        frameResourceWaitMs = 0f,
        acquireWaitMs = 0f,
    )

    @Test
    fun theUpscaleAndUiReadTogetherAsTheirSum() {
        assertEquals(5.5f, timing(upscale = 2f, ui = 3.5f).upscaleAndUiMs)
        assertEquals(2f, timing(upscale = 2f, ui = 3.5f).partMs(GpuFramePart.Upscale), "each part still reads on its own")
    }

    @Test
    fun aPartNotReportedYetLeavesTheSumUnreported() {
        assertNull(timing(upscale = Float.NaN, ui = 3.5f).upscaleAndUiMs)
        assertNull(timing(upscale = 2f, ui = Float.NaN).upscaleAndUiMs)
    }
}
