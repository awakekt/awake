/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.diagnostics.FrameBreakdown
import com.awakekt.awake.core.diagnostics.FrameUiUploadSample
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.renderer.UiUploadStats
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The renderer's UI mesh uploads reach the scene's frame stats and the frame breakdown, so a UI that
 * re-uploads on still frames shows up in a profile rather than only inside the renderer (#1545).
 */
class SceneFrameUiUploadsTest {
    private val renderer = object : NoopRenderer() {
        var uploads: UiUploadStats? = UiUploadStats(bytes = 4_096, runs = 3, triangles = 210)
        override val frameUiUploads: UiUploadStats? get() = uploads
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
    fun aRuntimeWithoutARendererReportsNoUploads() {
        // Headless, or before the backend started: "not counted", never "uploaded nothing".
        assertNull(runtime.frameStats().uiUploads)
    }

    @Test
    fun theRenderersUploadsReachFrameStatsAndTheBreakdownSample() = runTest {
        runtime.ready(renderer)

        val stats = runtime.frameStats()

        assertEquals(UiUploadStats(bytes = 4_096, runs = 3, triangles = 210), stats.uiUploads)
        assertEquals(FrameUiUploadSample(bytes = 4_096, runs = 3, triangles = 210), stats.toBreakdownSample().uiUploads)
    }

    @Test
    fun aRendererThatDoesNotCountLeavesThemOut() = runTest {
        renderer.uploads = null
        runtime.ready(renderer)

        assertNull(runtime.frameStats().uiUploads)
        assertNull(runtime.frameStats().toBreakdownSample().uiUploads)
    }

    @Test
    fun theSceneBreakdownSummarizesUploadsPerFrame() = runTest {
        runtime.ready(renderer)
        val breakdown = FrameBreakdown.forScene(runtime = { runtime }, renderer = renderer)

        breakdown.frame(nowSeconds = 1.0, durationSeconds = 1.0 / 60.0)
        renderer.uploads = UiUploadStats(bytes = 0, runs = 0, triangles = 0)
        breakdown.frame(nowSeconds = 1.1, durationSeconds = 1.0 / 60.0)

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 1.1, windowSeconds = 10.0))
        assertEquals(2_048.0, summaries.getValue(FrameBreakdown.KEY_UI_UPLOAD_BYTES).avg)
        assertEquals(3.0, summaries.getValue(FrameBreakdown.KEY_UI_UPLOAD_RUNS).max)
        assertEquals(105.0, summaries.getValue(FrameBreakdown.KEY_UI_UPLOAD_TRIANGLES).avg)
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }
}
