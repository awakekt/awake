/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FrameBreakdownTest {
    @Test
    fun attributesMeasuredPhasesOutsideTimeDrawCountsAndSurfaceMegapixels() {
        val breakdown = FrameBreakdown(
            sample = { FrameBreakdownSample(phases = PHASES, render = RENDER) },
            surfaceMpx = { 2.1 },
        )
        repeat(10) { step ->
            breakdown.frame(nowSeconds = step * 0.04, durationSeconds = 0.04)
        }

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 0.4, windowSeconds = 30.0))
        assertEquals(6.0, summaries.getValue(FrameBreakdown.KEY_SIM_MS).avg)
        assertEquals(3.0, summaries.getValue(FrameBreakdown.KEY_RENDER_SYSTEMS_MS).avg)
        assertEquals(10.0, summaries.getValue(FrameBreakdown.KEY_SIM_RENDER_MS).avg)
        assertEquals(4.0, summaries.getValue(FrameBreakdown.KEY_UI_BUILD_MS).avg)
        assertEquals(2.0, summaries.getValue(FrameBreakdown.KEY_UI_STAGE_MS).avg)
        assertEquals(1.0, summaries.getValue(FrameBreakdown.KEY_UI_WAIT_MS).avg)
        assertEquals(23.0, summaries.getValue(FrameBreakdown.KEY_OUTSIDE_MS).avg)
        assertEquals(12.5, summaries.getValue(FrameBreakdown.KEY_GPU_MS).p50)
        assertEquals(180.0, summaries.getValue(FrameBreakdown.KEY_DRAW_CALLS).max)
        assertEquals(240.0, summaries.getValue(FrameBreakdown.KEY_INSTANCES).avg)
        assertEquals(250_000.0, summaries.getValue(FrameBreakdown.KEY_TRIANGLES).avg)
        assertEquals(2.1, summaries.getValue(FrameBreakdown.KEY_SURFACE_MPX).avg)
        assertNull(summaries[FrameBreakdown.KEY_HEAP_MB])
    }

    @Test
    fun omitsUnmeasuredPhasesAndUncountedRenderer() {
        val breakdown = FrameBreakdown(
            sample = { FrameBreakdownSample(phases = FramePhaseSample(), render = null) },
        )
        breakdown.frame(nowSeconds = 0.0, durationSeconds = 0.04)
        assertNull(breakdown.summaries(nowSeconds = 0.0, windowSeconds = 30.0))
    }

    @Test
    fun tracksHeapGrowthAsAllocationAndHeapDropsAsCollectionsPerMinute() {
        val readings = ArrayDeque(listOf(100.0, 110.0, 120.0, 60.0, 70.0).map { it * KB })
        val breakdown = FrameBreakdown(
            sample = { FrameBreakdownSample(phases = PHASES, render = RENDER) },
            heapBytes = { readings.removeFirstOrNull() },
        )
        repeat(5) { step ->
            breakdown.frame(nowSeconds = step.toDouble(), durationSeconds = 1.0)
        }

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 4.0, windowSeconds = 30.0))
        assertEquals(10.0, summaries.getValue(FrameBreakdown.KEY_ALLOC_KB_PER_FRAME).avg)
        assertEquals(2.0, summaries.getValue(FrameBreakdown.KEY_GC_PER_MINUTE).avg)
    }

    @Test
    fun recordsHostHeapAndSurfaceEvenWithoutActiveScene() {
        val breakdown = FrameBreakdown(
            sample = { null },
            heapBytes = { 64.0 * KB * KB },
            surfaceMpx = { 2.1 },
        )
        repeat(3) { step ->
            breakdown.frame(nowSeconds = step.toDouble(), durationSeconds = 1.0)
        }

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 2.0, windowSeconds = 30.0))
        assertEquals(64.0, summaries.getValue(FrameBreakdown.KEY_HEAP_MB).avg)
        assertEquals(2.1, summaries.getValue(FrameBreakdown.KEY_SURFACE_MPX).avg)
        assertNull(summaries[FrameBreakdown.KEY_SIM_MS])

        breakdown.clear()
        assertNull(breakdown.summaries(nowSeconds = 2.0, windowSeconds = 30.0))
    }

    @Test
    fun validatesSummaryWindowArguments() {
        val breakdown = FrameBreakdown()
        assertFailsWith<IllegalArgumentException> { breakdown.summaries(Double.NaN, 30.0) }
        assertFailsWith<IllegalArgumentException> { breakdown.summaries(1.0, 0.0) }
    }

    private companion object {
        const val KB = 1_024.0
        val PHASES = FramePhaseSample(
            simMs = 6f,
            renderSystemsMs = 3f,
            simRenderMs = 10f,
            uiBuildMs = 4f,
            uiStageMs = 2f,
            uiWaitMs = 1f,
        )
        val RENDER = FrameRenderSample(
            drawCalls = 180,
            instances = 240,
            triangles = 250_000,
            gpuTimeMs = 12.5f,
        )
    }
}
