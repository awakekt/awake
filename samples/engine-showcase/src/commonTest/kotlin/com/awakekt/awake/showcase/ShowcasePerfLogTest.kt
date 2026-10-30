/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.renderer.RenderFrameStats
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.runtime.SceneAppSpec
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The old 240-frame-only logger emitted zero startup proof lines in the first test (expected one),
 * and only the empty diagnostic in the second (expected a later UI proof too). The fixture builds
 * real canvas primitives; controlled backend counters distinguish UI from actual draw evidence.
 */
class ShowcasePerfLogTest {
    @Test
    fun startupProofRequiresUiAndBackendDrawsThenKeepsPeriodicCadence() = runTest {
        val fixture = Fixture()
        fixture.runtime.ready(fixture.renderer)
        try {
            fixture.tick()
            fixture.renderer.frameStats = DRAWN
            fixture.tick()
            assertTrue(fixture.lines.isEmpty(), "backend draws without UI are not launch proof")

            fixture.hud.visible = true
            fixture.renderer.frameStats = null
            fixture.tick()
            assertTrue(fixture.runtime.uiPrimitives.isNotEmpty(), "the fixture must build real canvas UI")
            fixture.renderer.frameStats = DRAWN.copy(drawCalls = 0)
            fixture.tick()
            assertTrue(fixture.lines.isEmpty(), "UI without a submitted backend draw is not launch proof")

            fixture.renderer.frameStats = DRAWN
            fixture.tick()
            assertEquals(1, fixture.lines.size, "first UI proof must not wait 240 frames on a slow emulator")
            assertTrue(LAUNCH_PROOF.containsMatchIn(fixture.lines.single()))

            repeat(PERF_LOG_INTERVAL_FRAMES - 1) { fixture.tick() }
            assertEquals(1, fixture.lines.size, "startup proof must not turn into per-frame logging")
            fixture.tick()
            assertEquals(2, fixture.lines.size)
        } finally {
            fixture.runtime.dispose()
        }
    }

    @Test
    fun periodicEmptyFrameDiagnosticsDoNotSuppressLaterStartupProof() = runTest {
        val fixture = Fixture()
        fixture.runtime.ready(fixture.renderer)
        try {
            repeat(PERF_LOG_INTERVAL_FRAMES) { fixture.tick() }
            assertEquals(1, fixture.lines.size, "empty frames keep the existing diagnostic cadence")
            assertFalse(LAUNCH_PROOF.containsMatchIn(fixture.lines.single()))

            fixture.hud.visible = true
            fixture.renderer.frameStats = DRAWN
            fixture.tick()
            assertEquals(2, fixture.lines.size)
            assertTrue(LAUNCH_PROOF.containsMatchIn(fixture.lines.last()))
        } finally {
            fixture.runtime.dispose()
        }
    }

    private class FrameRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override var frameStats: RenderFrameStats? = null
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private class Fixture {
        val host = AppSpecBuilder()
        val renderer = FrameRenderer()
        val hud = CanvasElement().apply {
            width = 24f
            height = 24f
            background = "#ffffff"
            visible = false
        }
        val runtime = SceneAppSpec(
            sceneName = null,
            systems = emptyList(),
            scenePopulationBlock = { world.add(world.create(), hud) },
            renderableFactory = { error("no renderable requested") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
        ).installInto(host)
        val lines = mutableListOf<String>()
        val logger = ShowcasePerfLog(runtime, writeLine = lines::add) { "launch-probe" }

        fun tick() {
            runtime.update(AppFrame(1f / 4f, 320f, 640f, host.input.snapshot()))
            logger.update(runtime.world, 1f / 4f)
        }
    }

    private companion object {
        val DRAWN = RenderFrameStats(drawCalls = 3, instances = 3, triangles = 2)
        val LAUNCH_PROOF = Regex("PERF .* uiPrimitives=[1-9][0-9]* .* draws=[1-9][0-9]* ")
    }
}
