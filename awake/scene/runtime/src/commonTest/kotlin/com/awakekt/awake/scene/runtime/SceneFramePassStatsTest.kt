/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.diagnostics.FrameBreakdown
import com.awakekt.awake.core.diagnostics.FramePassSample
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.renderer.RenderPassCounts
import com.awakekt.awake.render.renderer.RenderPassStats
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The renderer's per-pass counts reach the scene's frame stats and the frame breakdown, so a profile
 * shows where a frame's draws went (scene, shadow cascades, UI) rather than only the renderer (#1545).
 */
class SceneFramePassStatsTest {
    private val renderer = object : NoopRenderer() {
        var passes: RenderPassStats? = PASSES
        var renderPasses = 9
        override val framePassStats: RenderPassStats? get() = passes
        override val frameRenderPasses: Int get() = renderPasses
    }

    private val runtime = SceneAppLifecycleRuntime(
        SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = {},
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { emptyList() },
        ),
    ).also { it.initialize(NoServices) }

    @Test
    fun aRuntimeWithoutARendererReportsNoPasses() {
        // Headless, or before the backend started: "not counted", never "drew nothing".
        val stats = runtime.frameStats()

        assertNull(stats.passes)
        assertNull(stats.renderPasses)
        assertNull(stats.toBreakdownSample().passes)
        assertNull(stats.toBreakdownSample().renderPasses)
    }

    @Test
    fun theRenderersPassCountsReachFrameStatsAndTheBreakdownSample() = runTest {
        runtime.ready(renderer)

        val stats = runtime.frameStats()

        assertEquals(PASSES, stats.passes)
        assertEquals(9, stats.renderPasses)
        val sample = stats.toBreakdownSample()
        assertEquals(
            FramePassSample(
                sceneOpaqueDraws = 120,
                sceneTransparentDraws = 8,
                shadowDraws = 300,
                shadowPasses = 2,
                uiDraws = 14,
                otherDraws = 3,
            ),
            sample.passes,
        )
        assertEquals(9, sample.renderPasses)
    }

    @Test
    fun aRendererThatDoesNotCountLeavesThemOut() = runTest {
        renderer.passes = null
        renderer.renderPasses = 0
        runtime.ready(renderer)

        val stats = runtime.frameStats()

        assertNull(stats.passes)
        assertNull(stats.renderPasses)
        assertNull(stats.toBreakdownSample().passes)
        assertNull(stats.toBreakdownSample().renderPasses)
    }

    @Test
    fun theSceneBreakdownSummarizesPassesPerFrame() = runTest {
        runtime.ready(renderer)
        val breakdown = FrameBreakdown.forScene(runtime = { runtime }, renderer = renderer)

        breakdown.frame(nowSeconds = 1.0, durationSeconds = 1.0 / 60.0)
        renderer.passes = PASSES.copy(shadowPasses = emptyList())
        renderer.renderPasses = 7
        breakdown.frame(nowSeconds = 1.1, durationSeconds = 1.0 / 60.0)

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 1.1, windowSeconds = 10.0))
        assertEquals(8.0, summaries.getValue(FrameBreakdown.KEY_RENDER_PASSES).avg)
        assertEquals(1.0, summaries.getValue(FrameBreakdown.KEY_SHADOW_PASSES).avg)
        assertEquals(150.0, summaries.getValue(FrameBreakdown.KEY_SHADOW_DRAWS).avg)
        assertEquals(120.0, summaries.getValue(FrameBreakdown.KEY_SCENE_OPAQUE_DRAWS).avg)
        assertEquals(8.0, summaries.getValue(FrameBreakdown.KEY_SCENE_TRANSPARENT_DRAWS).max)
        assertEquals(14.0, summaries.getValue(FrameBreakdown.KEY_UI_DRAWS).avg)
        assertEquals(3.0, summaries.getValue(FrameBreakdown.KEY_OTHER_DRAWS).avg)
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }

    private companion object {
        val PASSES = RenderPassStats(
            sceneOpaque = RenderPassCounts(drawCalls = 120, instances = 400, triangles = 90_000),
            sceneTransparent = RenderPassCounts(drawCalls = 8, instances = 8, triangles = 1_200),
            shadowPasses = listOf(
                RenderPassCounts(drawCalls = 180, instances = 260, triangles = 70_000),
                RenderPassCounts(drawCalls = 120, instances = 160, triangles = 40_000),
            ),
            ui = RenderPassCounts(drawCalls = 14, instances = 14, triangles = 600),
            other = RenderPassCounts(drawCalls = 3, instances = 3, triangles = 24),
        )
    }
}
