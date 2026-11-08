/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.diagnostics.FrameBreakdown
import com.awakekt.awake.core.diagnostics.FrameBreakdownSample
import com.awakekt.awake.core.diagnostics.FramePhaseSample
import com.awakekt.awake.core.diagnostics.FrameRenderSample
import com.awakekt.awake.render.renderer.Renderer

private const val PIXELS_PER_MEGAPIXEL = 1_000_000.0

/**
 * Converts a [SceneFrameStats] snapshot into a scene-neutral [FrameBreakdownSample].
 *
 * @return Phase and render submission readings ready for [FrameBreakdown].
 */
fun SceneFrameStats.toBreakdownSample(): FrameBreakdownSample =
    FrameBreakdownSample(
        phases = FramePhaseSample(
            simMs = phases.gameMs,
            renderSystemsMs = phases.renderMs,
            simRenderMs = phases.simRenderMs,
            uiBuildMs = phases.uiBuildMs,
            uiStageMs = phases.uiStageMs,
            uiWaitMs = phases.uiWaitMs,
        ),
        render = render?.let { stats ->
            FrameRenderSample(
                drawCalls = stats.drawCalls,
                instances = stats.instances,
                triangles = stats.triangles,
                gpuTimeMs = stats.gpuTimeMs,
            )
        },
    )

/**
 * Creates a [FrameBreakdown] wired to the active [SceneAppLifecycleRuntime] and optional [renderer].
 *
 * Sampling enables [SceneAppLifecycleRuntime.perfStatsEnabled] on the active runtime so phase
 * timers are collected while the breakdown is driven.
 *
 * @param runtime Returns the currently playing scene lifecycle runtime, or `null` between scenes.
 * @param heapBytes Returns current host heap usage in bytes, or `null` when unavailable.
 * @param renderer Optional renderer used to report drawable surface megapixels.
 * @param capacity Per-series frame sample capacity retained in memory.
 * @return Configured [FrameBreakdown] instance.
 */
fun FrameBreakdown.Companion.forScene(
    runtime: () -> SceneAppLifecycleRuntime?,
    heapBytes: () -> Double? = { null },
    renderer: Renderer? = null,
    capacity: Int = DEFAULT_CAPACITY,
): FrameBreakdown =
    FrameBreakdown(
        sample = {
            runtime()?.let { scene ->
                if (!scene.perfStatsEnabled) {
                    scene.perfStatsEnabled = true
                }
                scene.frameStats().toBreakdownSample()
            }
        },
        heapBytes = heapBytes,
        surfaceMpx = {
            renderer?.let {
                it.surfaceWidth.toDouble() * it.surfaceHeight.toDouble() / PIXELS_PER_MEGAPIXEL
            }
        },
        capacity = capacity,
    )
