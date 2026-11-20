/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.math.round

/**
 * Rolling window aggregator that attributes each frame's wall time across simulation, rendering, UI,
 * external host/GPU wait (`outsideMs`), renderer draw counts and where they went by pass, UI mesh
 * uploads, GPU buffers created, drawable megapixels, and heap growth or garbage-collection frequency.
 *
 * Call [frame] once per rendered frame on the frame thread and [summaries] whenever a diagnostic
 * snapshot is requested. Parts without readings in the requested window are omitted rather than
 * reported as zero.
 *
 * @param sample Supplies the current frame's phase and render counts, or `null` when no scene is active.
 * @param heapBytes Supplies current managed/JS heap usage in bytes, or `null` when unavailable on the host.
 * @param surfaceMpx Supplies current drawable area in megapixels, or `null` when headless.
 * @property capacity Per-series sample capacity retained in memory (`>= 1`).
 */
class FrameBreakdown(
    private val sample: () -> FrameBreakdownSample? = { null },
    private val heapBytes: () -> Double? = { null },
    private val surfaceMpx: () -> Double? = { null },
    val capacity: Int = DEFAULT_CAPACITY,
) {
    private val series = Array(Part.entries.size) { StatsSeries(capacity) }
    private val collections = ArrayDeque<Double>()
    private var lastHeap = Double.NaN

    /**
     * Records a frame of [durationSeconds] ending at [nowSeconds].
     *
     * @param nowSeconds Monotonic timestamp in seconds when the frame completed.
     * @param durationSeconds Total wall-clock duration of the frame in seconds.
     */
    fun frame(nowSeconds: Double, durationSeconds: Double) {
        surfaceMpx()?.let { series[Part.SurfaceMpx.ordinal].add(nowSeconds, it) }
        recordHeap(nowSeconds)
        val current = sample() ?: return
        val phases = current.phases
        if (phases.isMeasured) {
            series[Part.Sim.ordinal].add(nowSeconds, phases.simMs.toDouble())
            series[Part.RenderSystems.ordinal].add(nowSeconds, phases.renderSystemsMs.toDouble())
            series[Part.SimRender.ordinal].add(nowSeconds, phases.simRenderMs.toDouble())
            series[Part.UiBuild.ordinal].add(nowSeconds, phases.uiBuildMs.toDouble())
            series[Part.UiStage.ordinal].add(nowSeconds, phases.uiStageMs.toDouble())
            series[Part.UiWait.ordinal].add(nowSeconds, phases.uiWaitMs.toDouble())
            val inside =
                phases.simRenderMs + phases.uiBuildMs + phases.uiStageMs + phases.uiWaitMs
            val outside = (durationSeconds * MILLIS_PER_SECOND - inside).coerceAtLeast(0.0)
            series[Part.Outside.ordinal].add(nowSeconds, outside)
        }
        current.render?.let { render ->
            render.gpuTimeMs?.let { series[Part.Gpu.ordinal].add(nowSeconds, it.toDouble()) }
            series[Part.DrawCalls.ordinal].add(nowSeconds, render.drawCalls.toDouble())
            series[Part.Instances.ordinal].add(nowSeconds, render.instances.toDouble())
            series[Part.Triangles.ordinal].add(nowSeconds, render.triangles.toDouble())
        }
        current.uiUploads?.let { uploads ->
            series[Part.UiUploadBytes.ordinal].add(nowSeconds, uploads.bytes.toDouble())
            series[Part.UiUploadRuns.ordinal].add(nowSeconds, uploads.runs.toDouble())
            series[Part.UiUploadTriangles.ordinal].add(nowSeconds, uploads.triangles.toDouble())
        }
        current.passes?.let { passes ->
            series[Part.SceneOpaqueDraws.ordinal].add(nowSeconds, passes.sceneOpaqueDraws.toDouble())
            series[Part.SceneTransparentDraws.ordinal].add(nowSeconds, passes.sceneTransparentDraws.toDouble())
            series[Part.ShadowDraws.ordinal].add(nowSeconds, passes.shadowDraws.toDouble())
            series[Part.ShadowPasses.ordinal].add(nowSeconds, passes.shadowPasses.toDouble())
            series[Part.UiDraws.ordinal].add(nowSeconds, passes.uiDraws.toDouble())
            series[Part.OtherDraws.ordinal].add(nowSeconds, passes.otherDraws.toDouble())
        }
        current.renderPasses?.let { series[Part.RenderPasses.ordinal].add(nowSeconds, it.toDouble()) }
        current.bufferCreations?.let { created ->
            series[Part.ArgumentBuffersCreated.ordinal].add(nowSeconds, created.argumentBuffers.toDouble())
            series[Part.InstanceBuffersCreated.ordinal].add(nowSeconds, created.instanceBuffers.toDouble())
            series[Part.UniformBuffersCreated.ordinal].add(nowSeconds, created.uniformBuffers.toDouble())
        }
        current.gpu?.let { recordGpu(nowSeconds, it) }
    }

    private fun recordGpu(nowSeconds: Double, gpu: FrameGpuSample) {
        gpu.shadowDepthMs?.let { series[Part.GpuShadowDepth.ordinal].add(nowSeconds, it.toDouble()) }
        gpu.sceneDepthMs?.let { series[Part.GpuSceneDepth.ordinal].add(nowSeconds, it.toDouble()) }
        gpu.maskMs?.let { series[Part.GpuMask.ordinal].add(nowSeconds, it.toDouble()) }
        gpu.sceneMs?.let { series[Part.GpuScene.ordinal].add(nowSeconds, it.toDouble()) }
        gpu.postMs?.let { series[Part.GpuPost.ordinal].add(nowSeconds, it.toDouble()) }
        gpu.upscaleMs?.let { series[Part.GpuUpscale.ordinal].add(nowSeconds, it.toDouble()) }
        gpu.uiMs?.let { series[Part.GpuUi.ordinal].add(nowSeconds, it.toDouble()) }
        series[Part.FrameResourceWait.ordinal].add(nowSeconds, gpu.frameResourceWaitMs.toDouble())
        series[Part.AcquireWait.ordinal].add(nowSeconds, gpu.acquireWaitMs.toDouble())
    }

    /**
     * Summarizes every recorded part over `[nowSeconds - windowSeconds, nowSeconds]`.
     *
     * @param nowSeconds Upper timestamp bound of the window in seconds.
     * @param windowSeconds Positive trailing window length in seconds.
     * @return Map from metric key to [StatsSummary] for parts with readings, or `null` when empty.
     */
    fun summaries(
        nowSeconds: Double,
        windowSeconds: Double = DEFAULT_WINDOW_SECONDS,
    ): Map<String, StatsSummary>? {
        require(nowSeconds.isFinite()) {
            "nowSeconds must be finite, was $nowSeconds."
        }
        require(windowSeconds > 0.0 && windowSeconds.isFinite()) {
            "windowSeconds must be finite and positive, was $windowSeconds."
        }
        val since = nowSeconds - windowSeconds
        val parts = LinkedHashMap<String, StatsSummary>(Part.entries.size + 1)
        for (part in Part.entries) {
            series[part.ordinal].summary(since)?.let { parts[part.key] = it }
        }
        while (collections.isNotEmpty() && collections.first() < since) {
            collections.removeFirst()
        }
        if (!lastHeap.isNaN()) {
            val perMinute =
                round(collections.size * SECONDS_PER_MINUTE / windowSeconds * TENTHS) / TENTHS
            parts[KEY_GC_PER_MINUTE] = StatsSummary(perMinute, perMinute, perMinute, perMinute)
        }
        return parts.ifEmpty { null }
    }

    /** Clears all retained frame series and heap-tracking history. */
    fun clear() {
        for (partSeries in series) {
            partSeries.clear()
        }
        collections.clear()
        lastHeap = Double.NaN
    }

    private fun recordHeap(nowSeconds: Double) {
        val bytes = heapBytes()?.takeIf { it.isFinite() } ?: return
        series[Part.HeapMb.ordinal].add(nowSeconds, bytes / BYTES_PER_MB)
        if (!lastHeap.isNaN()) {
            val grown = bytes - lastHeap
            if (grown >= 0.0) {
                series[Part.AllocKbPerFrame.ordinal].add(nowSeconds, grown / BYTES_PER_KB)
            } else {
                collections.addLast(nowSeconds)
            }
        }
        lastHeap = bytes
    }

    private enum class Part(val key: String) {
        Sim(KEY_SIM_MS),
        RenderSystems(KEY_RENDER_SYSTEMS_MS),
        SimRender(KEY_SIM_RENDER_MS),
        UiBuild(KEY_UI_BUILD_MS),
        UiStage(KEY_UI_STAGE_MS),
        UiWait(KEY_UI_WAIT_MS),
        Outside(KEY_OUTSIDE_MS),
        Gpu(KEY_GPU_MS),
        DrawCalls(KEY_DRAW_CALLS),
        Instances(KEY_INSTANCES),
        Triangles(KEY_TRIANGLES),
        SceneOpaqueDraws(KEY_SCENE_OPAQUE_DRAWS),
        SceneTransparentDraws(KEY_SCENE_TRANSPARENT_DRAWS),
        ShadowDraws(KEY_SHADOW_DRAWS),
        ShadowPasses(KEY_SHADOW_PASSES),
        UiDraws(KEY_UI_DRAWS),
        OtherDraws(KEY_OTHER_DRAWS),
        RenderPasses(KEY_RENDER_PASSES),
        UiUploadBytes(KEY_UI_UPLOAD_BYTES),
        UiUploadRuns(KEY_UI_UPLOAD_RUNS),
        UiUploadTriangles(KEY_UI_UPLOAD_TRIANGLES),
        ArgumentBuffersCreated(KEY_ARGUMENT_BUFFERS_CREATED),
        InstanceBuffersCreated(KEY_INSTANCE_BUFFERS_CREATED),
        UniformBuffersCreated(KEY_UNIFORM_BUFFERS_CREATED),
        GpuShadowDepth(KEY_GPU_SHADOW_DEPTH_MS),
        GpuSceneDepth(KEY_GPU_SCENE_DEPTH_MS),
        GpuMask(KEY_GPU_MASK_MS),
        GpuScene(KEY_GPU_SCENE_MS),
        GpuPost(KEY_GPU_POST_MS),
        GpuUpscale(KEY_GPU_UPSCALE_MS),
        GpuUi(KEY_GPU_UI_MS),
        FrameResourceWait(KEY_FRAME_RESOURCE_WAIT_MS),
        AcquireWait(KEY_ACQUIRE_WAIT_MS),
        SurfaceMpx(KEY_SURFACE_MPX),
        HeapMb(KEY_HEAP_MB),
        AllocKbPerFrame(KEY_ALLOC_KB_PER_FRAME),
    }

    /** Metric keys and default tuning constants for [FrameBreakdown]. */
    companion object {
        /** Default per-part ring capacity (4,400 frames: 30 seconds at 144 Hz with headroom). */
        const val DEFAULT_CAPACITY: Int = 4_400

        /** Default trailing summary window in seconds (30 s). */
        const val DEFAULT_WINDOW_SECONDS: Double = 30.0

        /** Summary key for gameplay simulation CPU time in milliseconds. */
        const val KEY_SIM_MS: String = "simMs"

        /** Summary key for transform and render-extraction system CPU time in milliseconds. */
        const val KEY_RENDER_SYSTEMS_MS: String = "renderSystemsMs"

        /** Summary key for total scene update CPU time in milliseconds. */
        const val KEY_SIM_RENDER_MS: String = "simRenderMs"

        /** Summary key for UI composition and layout CPU time in milliseconds. */
        const val KEY_UI_BUILD_MS: String = "uiBuildMs"

        /** Summary key for UI tessellation and staging CPU time in milliseconds. */
        const val KEY_UI_STAGE_MS: String = "uiStageMs"

        /** Summary key for UI GPU resource wait time in milliseconds. */
        const val KEY_UI_WAIT_MS: String = "uiWaitMs"

        /** Summary key for frame wall time outside measured scene and UI phases in milliseconds. */
        const val KEY_OUTSIDE_MS: String = "outsideMs"

        /** Summary key for GPU execution time in milliseconds. */
        const val KEY_GPU_MS: String = "gpuMs"

        /** Summary key for submitted draw calls per frame. */
        const val KEY_DRAW_CALLS: String = "drawCalls"

        /** Summary key for submitted instances per frame. */
        const val KEY_INSTANCES: String = "instances"

        /** Summary key for submitted triangles per frame. */
        const val KEY_TRIANGLES: String = "triangles"

        /** Summary key for opaque scene-pass draws per frame. */
        const val KEY_SCENE_OPAQUE_DRAWS: String = "sceneOpaqueDraws"

        /** Summary key for blended scene-pass draws per frame. */
        const val KEY_SCENE_TRANSPARENT_DRAWS: String = "sceneTransparentDraws"

        /** Summary key for draws across every shadow depth pass per frame. */
        const val KEY_SHADOW_DRAWS: String = "shadowDraws"

        /** Summary key for shadow depth passes per frame (cascades and point-shadow faces). */
        const val KEY_SHADOW_PASSES: String = "shadowPasses"

        /** Summary key for UI-pass draws per frame. */
        const val KEY_UI_DRAWS: String = "uiDraws"

        /** Summary key for draws outside the scene, shadow, and UI passes per frame. */
        const val KEY_OTHER_DRAWS: String = "otherDraws"

        /** Summary key for render passes begun per frame, offscreen passes included. */
        const val KEY_RENDER_PASSES: String = "renderPasses"

        /** Summary key for UI mesh bytes uploaded per frame; a still UI reads zero. */
        const val KEY_UI_UPLOAD_BYTES: String = "uiUploadBytes"

        /** Summary key for UI draw runs whose mesh was uploaded per frame. */
        const val KEY_UI_UPLOAD_RUNS: String = "uiUploadRuns"

        /** Summary key for triangles in the UI draw runs uploaded per frame. */
        const val KEY_UI_UPLOAD_TRIANGLES: String = "uiUploadTriangles"

        /** Summary key for retained-bundle argument buffers created per frame; a warm frame reads zero. */
        const val KEY_ARGUMENT_BUFFERS_CREATED: String = "argumentBuffersCreated"

        /** Summary key for instanced draws' model buffers created per frame; a warm frame reads zero. */
        const val KEY_INSTANCE_BUFFERS_CREATED: String = "instanceBuffersCreated"

        /** Summary key for per-draw uniform buffers created per frame; a warm frame reads zero. */
        const val KEY_UNIFORM_BUFFERS_CREATED: String = "uniformBuffersCreated"

        /** Summary key for GPU milliseconds of the shadow cascades' depth passes per frame (#1709). */
        const val KEY_GPU_SHADOW_DEPTH_MS: String = "gpuShadowDepthMs"

        /** Summary key for GPU milliseconds of the camera's own depth pass per frame. */
        const val KEY_GPU_SCENE_DEPTH_MS: String = "gpuSceneDepthMs"

        /** Summary key for GPU milliseconds of the mask passes per frame. */
        const val KEY_GPU_MASK_MS: String = "gpuMaskMs"

        /** Summary key for GPU milliseconds of the scene pass per frame, at the render scale. */
        const val KEY_GPU_SCENE_MS: String = "gpuSceneMs"

        /** Summary key for GPU milliseconds of image passes on the scene before it is scaled. */
        const val KEY_GPU_POST_MS: String = "gpuPostMs"

        /** Summary key for GPU milliseconds of scaling the scene to the output and its image passes there. */
        const val KEY_GPU_UPSCALE_MS: String = "gpuUpscaleMs"

        /** Summary key for GPU milliseconds of the UI pass per frame. */
        const val KEY_GPU_UI_MS: String = "gpuUiMs"

        /** Summary key for CPU milliseconds blocked on fences for the GPU's earlier work per frame. */
        const val KEY_FRAME_RESOURCE_WAIT_MS: String = "frameResourceWaitMs"

        /** Summary key for CPU milliseconds blocked acquiring the next swapchain image per frame. */
        const val KEY_ACQUIRE_WAIT_MS: String = "acquireWaitMs"

        /** Summary key for drawable surface size in megapixels. */
        const val KEY_SURFACE_MPX: String = "surfaceMpx"

        /** Summary key for heap memory in use in megabytes. */
        const val KEY_HEAP_MB: String = "heapMb"

        /** Summary key for positive frame-to-frame heap growth in kilobytes. */
        const val KEY_ALLOC_KB_PER_FRAME: String = "allocKbPerFrame"

        /** Summary key for observed heap drops (collections) per minute. */
        const val KEY_GC_PER_MINUTE: String = "gcPerMinute"

        private const val TENTHS: Double = 10.0
        private const val MILLIS_PER_SECOND: Double = 1_000.0
        private const val SECONDS_PER_MINUTE: Double = 60.0
        private const val BYTES_PER_KB: Double = 1_024.0
        private const val BYTES_PER_MB: Double = 1_024.0 * 1_024.0
    }
}
