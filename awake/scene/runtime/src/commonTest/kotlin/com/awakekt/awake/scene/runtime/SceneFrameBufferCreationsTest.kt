/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.diagnostics.FrameBreakdown
import com.awakekt.awake.core.diagnostics.FrameBufferCreationSample
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.render.renderer.BufferCreationStats
import com.awakekt.awake.render.testing.NoopRenderer
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The GPU buffers the renderer's draw path created reach the scene's frame stats and the frame
 * breakdown, so a pool that is not holding shows up in a profile, not only inside the renderer (#1621).
 */
class SceneFrameBufferCreationsTest {
    private val renderer = object : NoopRenderer() {
        var created: BufferCreationStats? = BufferCreationStats(argumentBuffers = 4, instanceBuffers = 2, uniformBuffers = 30)
        override val frameBufferCreations: BufferCreationStats? get() = created
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
    fun aRuntimeWithoutARendererReportsNoBufferCreations() {
        // Headless, or before the backend started: "not counted", never "created nothing".
        assertNull(runtime.frameStats().bufferCreations)
    }

    @Test
    fun theRenderersCountsReachFrameStatsAndTheBreakdownSample() = runTest {
        runtime.ready(renderer)

        val stats = runtime.frameStats()

        assertEquals(BufferCreationStats(argumentBuffers = 4, instanceBuffers = 2, uniformBuffers = 30), stats.bufferCreations)
        assertEquals(
            FrameBufferCreationSample(argumentBuffers = 4, instanceBuffers = 2, uniformBuffers = 30),
            stats.toBreakdownSample().bufferCreations,
        )
    }

    @Test
    fun aRendererThatDoesNotCountLeavesThemOut() = runTest {
        renderer.created = null
        runtime.ready(renderer)

        assertNull(runtime.frameStats().bufferCreations)
        assertNull(runtime.frameStats().toBreakdownSample().bufferCreations)
    }

    @Test
    fun theSceneBreakdownSummarizesBufferCreationsPerFrame() = runTest {
        runtime.ready(renderer)
        val breakdown = FrameBreakdown.forScene(runtime = { runtime }, renderer = renderer)

        breakdown.frame(nowSeconds = 1.0, durationSeconds = 1.0 / 60.0)
        renderer.created = BufferCreationStats(argumentBuffers = 0, instanceBuffers = 0, uniformBuffers = 0)
        breakdown.frame(nowSeconds = 1.1, durationSeconds = 1.0 / 60.0)

        val summaries = checkNotNull(breakdown.summaries(nowSeconds = 1.1, windowSeconds = 10.0))
        assertEquals(2.0, summaries.getValue(FrameBreakdown.KEY_ARGUMENT_BUFFERS_CREATED).avg)
        assertEquals(2.0, summaries.getValue(FrameBreakdown.KEY_INSTANCE_BUFFERS_CREATED).max)
        assertEquals(15.0, summaries.getValue(FrameBreakdown.KEY_UNIFORM_BUFFERS_CREATED).avg)
    }

    private object NoServices : AppServiceLookup {
        override fun <T : Any> service(type: KClass<T>): T? = null
    }
}
