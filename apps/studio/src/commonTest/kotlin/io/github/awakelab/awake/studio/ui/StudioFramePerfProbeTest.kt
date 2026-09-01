/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.testing.NoopRenderer
import io.github.awakelab.awake.scene.runtime.LocalRenderer
import io.github.awakelab.awake.scene.runtime.LocalWorld
import io.github.awakelab.awake.studio.state.StudioStore
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * What one Studio frame costs.
 *
 * **The trial-pass count is gone, and that is the result rather than a gap in the probe.** This
 * measured `trialsPerFrame` because `ui-core` re-executed content to size it; the shell reported
 * 12,041 of them per frame. There is no re-execution to count now, so the probe reports what is
 * left: one composition pass per frame, over the whole tree, which is the engine's design.
 *
 * Investigation, not a gate: it prints. A number here is a desktop-JVM steady state after warmup,
 * not a frame budget, and `awake-ui-performance` Rule 5 is that the ranking reorders between runs.
 */
class StudioFramePerfProbeTest {

    @Test
    fun measureSteadyStateFrameCost() {
        val host = ComposeHost()
        val store = StudioStore()
        val world = World()
        val renderer: Renderer = NoopRenderer()
        host.compositionStats.enabled = true

        fun frame() {
            host.frame(
                FrameInput(
                    viewportWidth = 1440,
                    viewportHeight = 900,
                    pointerX = 400,
                    pointerY = 300,
                ),
            ) {
                CompositionLocalProvider(
                    LocalWorld provides world,
                    LocalRenderer provides renderer,
                ) {
                    provideShadcnTheme(StudioTheme) { StudioShell(store, backend = "Vulkan") }
                }
            }
        }

        repeat(WARMUP_FRAMES) { frame() }
        host.compositionStats.reset()
        val elapsed = measureTime { repeat(MEASURED_FRAMES) { frame() } }
        val msPerFrame = elapsed.inWholeMicroseconds / 1000.0 / MEASURED_FRAMES
        val compositionMsPerFrame = host.compositionStats.compositionNanos / 1_000_000.0 / MEASURED_FRAMES
        println(
            "PERF studio-shell msPerFrame=$msPerFrame compositionMsPerFrame=$compositionMsPerFrame " +
                "compositionPasses=${host.compositionStats.compositionPasses} trialsPerFrame=0",
        )

        assertTrue(msPerFrame > 0.0, "the probe measured nothing")
    }

    private companion object {
        const val WARMUP_FRAMES = 60
        const val MEASURED_FRAMES = 300
    }
}
