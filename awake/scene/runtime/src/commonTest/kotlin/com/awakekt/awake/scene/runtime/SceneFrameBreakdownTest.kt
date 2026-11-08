/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.diagnostics.FrameBreakdown
import com.awakekt.awake.render.renderer.RenderFrameStats
import com.awakekt.awake.render.testing.NoopRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneFrameBreakdownTest {
    @Test
    fun mapsSceneFrameStatsAndRendererSurfaceIntoFrameBreakdown() {
        val stats = SceneFrameStats(
            frameTimeMs = 16.0f,
            fps = 60.0f,
            trialPasses = 0,
            textCacheHits = 4,
            textCacheMisses = 1,
            phases = ScenePhaseStats(
                uiBuildMs = 2.0f,
                uiWaitMs = 0.5f,
                uiStageMs = 1.5f,
                simRenderMs = 8.0f,
                gameMs = 5.0f,
                renderMs = 2.5f,
            ),
            render = RenderFrameStats(
                drawCalls = 120,
                instances = 300,
                triangles = 90_000,
                gpuTimeMs = 6.5f,
            ),
        )
        val sample = stats.toBreakdownSample()
        assertTrue(sample.phases.isMeasured)
        assertEquals(5.0f, sample.phases.simMs)
        assertEquals(2.5f, sample.phases.renderSystemsMs)
        assertEquals(120, sample.render?.drawCalls)

        val renderer = object : NoopRenderer() {
            override val surfaceWidth: Int = 1_920
            override val surfaceHeight: Int = 1_080
        }
        val breakdown = FrameBreakdown.forScene(
            runtime = { null },
            heapBytes = { 32.0 * 1_024.0 * 1_024.0 },
            renderer = renderer,
        )
        breakdown.frame(nowSeconds = 1.0, durationSeconds = 1.0 / 60.0)

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 1.0, windowSeconds = 10.0))
        assertEquals(2.1, summaries.getValue(FrameBreakdown.KEY_SURFACE_MPX).avg)
        assertEquals(32.0, summaries.getValue(FrameBreakdown.KEY_HEAP_MB).avg)
    }
}
