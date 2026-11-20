/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.diagnostics.FrameBreakdown
import com.awakekt.awake.core.diagnostics.FrameBreakdownSample
import com.awakekt.awake.core.diagnostics.FrameBufferCreationSample
import com.awakekt.awake.core.diagnostics.FrameGpuSample
import com.awakekt.awake.core.diagnostics.FramePassSample
import com.awakekt.awake.core.diagnostics.FramePhaseSample
import com.awakekt.awake.core.diagnostics.FrameRenderSample
import com.awakekt.awake.core.diagnostics.FrameUiUploadSample
import com.awakekt.awake.render.renderer.FrameGpuTiming
import com.awakekt.awake.render.renderer.GpuFramePart
import com.awakekt.awake.render.renderer.Renderer

private const val PIXELS_PER_MEGAPIXEL = 1_000_000.0

/**
 * Converts a [SceneFrameStats] snapshot into a scene-neutral [FrameBreakdownSample].
 *
 * @return Phase, render submission, per-pass, UI upload, and buffer creation readings ready for [FrameBreakdown].
 */
fun SceneFrameStats.toBreakdownSample(): FrameBreakdownSample = toBreakdownSample(gpu = null)

/**
 * [toBreakdownSample], with [gpu]'s split of the frame's GPU time and the CPU's waits, as a renderer's
 * [Renderer.frameGpuTiming] reads (#1709).
 */
fun SceneFrameStats.toBreakdownSample(gpu: FrameGpuTiming?): FrameBreakdownSample =
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
        uiUploads = uiUploads?.let { FrameUiUploadSample(it.bytes, it.runs, it.triangles) },
        passes = passes?.let { stats ->
            FramePassSample(
                sceneOpaqueDraws = stats.sceneOpaque.drawCalls,
                sceneTransparentDraws = stats.sceneTransparent.drawCalls,
                shadowDraws = stats.shadow.drawCalls,
                shadowPasses = stats.shadowPasses.size,
                uiDraws = stats.ui.drawCalls,
                otherDraws = stats.other.drawCalls,
            )
        },
        renderPasses = renderPasses,
        bufferCreations = bufferCreations?.let {
            FrameBufferCreationSample(it.argumentBuffers, it.instanceBuffers, it.uniformBuffers)
        },
        gpu = gpu?.toSample(),
    )

private fun FrameGpuTiming.toSample(): FrameGpuSample = FrameGpuSample(
    shadowDepthMs = partMs(GpuFramePart.ShadowDepth),
    sceneDepthMs = partMs(GpuFramePart.SceneDepth),
    maskMs = partMs(GpuFramePart.Mask),
    sceneMs = partMs(GpuFramePart.Scene),
    postMs = partMs(GpuFramePart.Post),
    upscaleMs = partMs(GpuFramePart.Upscale),
    uiMs = partMs(GpuFramePart.Ui),
    frameResourceWaitMs = frameResourceWaitMs,
    acquireWaitMs = acquireWaitMs,
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
                scene.frameStats().toBreakdownSample(renderer?.frameGpuTiming)
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
