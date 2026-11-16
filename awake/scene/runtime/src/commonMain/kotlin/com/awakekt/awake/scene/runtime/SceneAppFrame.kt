/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.render.renderer.BufferCreationStats
import com.awakekt.awake.render.renderer.DynamicResolutionStats
import com.awakekt.awake.render.renderer.RenderFrameStats
import com.awakekt.awake.render.renderer.RenderPassStats
import com.awakekt.awake.render.renderer.UiUploadStats
import kotlin.math.roundToInt

/**
 * Performance metrics snapshot for a single scene execution frame.
 *
 * Same shape as [com.awakekt.awake.engine.application.GameFrameStats].
 *
 * @property frameTimeMs Average total frame execution duration in milliseconds.
 * @property fps Estimated current frames per second.
 * @property trialPasses Total number of speculative UI trial-measure passes performed.
 * @property textCacheHits Number of text layout cache hits during the frame.
 * @property textCacheMisses Number of text layout cache misses during the frame.
 * @property phases Phase attribution breakdown; zeroed unless performance metrics are enabled.
 * @property p99FrameTimeMs The 99th-percentile frame time over the recent sampling window in milliseconds.
 * @property maxFrameTimeMs The maximum single frame duration observed over the recent sampling window in milliseconds.
 * @property render Detailed GPU draw and geometry statistics from the active renderer, or `null` if uncounted.
 * @property uiUploads What the renderer's last frame wrote into UI meshes, or `null` before a renderer is
 * ready or when it does not count; a still UI reads zero. See [UiUploadStats].
 * @property passes The renderer's last frame split by pass (scene opaque, scene transparent, each shadow
 * pass, UI, other), or `null` before a renderer is ready or when it does not count. See [RenderPassStats].
 * @property renderPasses Render passes the renderer's last frame began, offscreen passes included, or
 * `null` before a renderer is ready or when it does not count them.
 * @property bufferCreations GPU buffers the draw path created during the renderer's last frame, by site,
 * or `null` before a renderer is ready or when it does not count; a warm frame reads zero. See
 * [BufferCreationStats].
 * @property resolution Where dynamic resolution stood after the frame: the short edge the scene was
 * asked to draw at and the signal it read, or `null` while it does not run (#1688). See
 * [DynamicResolutionStats].
 */
data class SceneFrameStats(
    val frameTimeMs: Float,
    val fps: Float,
    val trialPasses: Int,
    val textCacheHits: Int,
    val textCacheMisses: Int,
    val phases: ScenePhaseStats = ScenePhaseStats(),
    val p99FrameTimeMs: Float = 0f,
    val maxFrameTimeMs: Float = 0f,
    val render: RenderFrameStats? = null,
    val uiUploads: UiUploadStats? = null,
    val passes: RenderPassStats? = null,
    val renderPasses: Int? = null,
    val bufferCreations: BufferCreationStats? = null,
    val resolution: DynamicResolutionStats? = null,
) {
    /** The constructor as released before [resolution], kept so compiled callers still link. */
    @Deprecated("Use the constructor with resolution.", level = DeprecationLevel.HIDDEN)
    constructor(
        frameTimeMs: Float,
        fps: Float,
        trialPasses: Int,
        textCacheHits: Int,
        textCacheMisses: Int,
        phases: ScenePhaseStats = ScenePhaseStats(),
        p99FrameTimeMs: Float = 0f,
        maxFrameTimeMs: Float = 0f,
        render: RenderFrameStats? = null,
        uiUploads: UiUploadStats? = null,
        passes: RenderPassStats? = null,
        renderPasses: Int? = null,
        bufferCreations: BufferCreationStats? = null,
    ) : this(
        frameTimeMs, fps, trialPasses, textCacheHits, textCacheMisses, phases, p99FrameTimeMs, maxFrameTimeMs, render,
        uiUploads, passes, renderPasses, bufferCreations, null,
    )

    /** [copy] as released before [resolution]. */
    @Deprecated("Use copy with resolution.", level = DeprecationLevel.HIDDEN)
    fun copy(
        frameTimeMs: Float = this.frameTimeMs,
        fps: Float = this.fps,
        trialPasses: Int = this.trialPasses,
        textCacheHits: Int = this.textCacheHits,
        textCacheMisses: Int = this.textCacheMisses,
        phases: ScenePhaseStats = this.phases,
        p99FrameTimeMs: Float = this.p99FrameTimeMs,
        maxFrameTimeMs: Float = this.maxFrameTimeMs,
        render: RenderFrameStats? = this.render,
        uiUploads: UiUploadStats? = this.uiUploads,
        passes: RenderPassStats? = this.passes,
        renderPasses: Int? = this.renderPasses,
        bufferCreations: BufferCreationStats? = this.bufferCreations,
    ): SceneFrameStats = copy(
        frameTimeMs, fps, trialPasses, textCacheHits, textCacheMisses, phases, p99FrameTimeMs, maxFrameTimeMs, render,
        uiUploads, passes, renderPasses, bufferCreations, resolution,
    )

    /** The constructor as released before [uiUploads], kept so compiled callers still link. */
    @Deprecated("Use the constructor with uiUploads.", level = DeprecationLevel.HIDDEN)
    constructor(
        frameTimeMs: Float,
        fps: Float,
        trialPasses: Int,
        textCacheHits: Int,
        textCacheMisses: Int,
        phases: ScenePhaseStats = ScenePhaseStats(),
        p99FrameTimeMs: Float = 0f,
        maxFrameTimeMs: Float = 0f,
        render: RenderFrameStats? = null,
    ) : this(
        frameTimeMs, fps, trialPasses, textCacheHits, textCacheMisses, phases, p99FrameTimeMs, maxFrameTimeMs, render,
        null, null, null, null, null,
    )

    /** The constructor as released before [passes] and [renderPasses], kept so compiled callers still link. */
    @Deprecated("Use the constructor with passes and renderPasses.", level = DeprecationLevel.HIDDEN)
    constructor(
        frameTimeMs: Float,
        fps: Float,
        trialPasses: Int,
        textCacheHits: Int,
        textCacheMisses: Int,
        phases: ScenePhaseStats = ScenePhaseStats(),
        p99FrameTimeMs: Float = 0f,
        maxFrameTimeMs: Float = 0f,
        render: RenderFrameStats? = null,
        uiUploads: UiUploadStats? = null,
    ) : this(
        frameTimeMs, fps, trialPasses, textCacheHits, textCacheMisses, phases, p99FrameTimeMs, maxFrameTimeMs, render,
        uiUploads, null, null, null, null,
    )

    /** [copy] as released before [uiUploads]. */
    @Deprecated("Use copy with uiUploads.", level = DeprecationLevel.HIDDEN)
    fun copy(
        frameTimeMs: Float = this.frameTimeMs,
        fps: Float = this.fps,
        trialPasses: Int = this.trialPasses,
        textCacheHits: Int = this.textCacheHits,
        textCacheMisses: Int = this.textCacheMisses,
        phases: ScenePhaseStats = this.phases,
        p99FrameTimeMs: Float = this.p99FrameTimeMs,
        maxFrameTimeMs: Float = this.maxFrameTimeMs,
        render: RenderFrameStats? = this.render,
    ): SceneFrameStats = copy(
        frameTimeMs, fps, trialPasses, textCacheHits, textCacheMisses, phases, p99FrameTimeMs, maxFrameTimeMs, render,
        uiUploads, passes, renderPasses, bufferCreations, resolution,
    )

    /** [copy] as released before [passes] and [renderPasses]. */
    @Deprecated("Use copy with passes and renderPasses.", level = DeprecationLevel.HIDDEN)
    fun copy(
        frameTimeMs: Float = this.frameTimeMs,
        fps: Float = this.fps,
        trialPasses: Int = this.trialPasses,
        textCacheHits: Int = this.textCacheHits,
        textCacheMisses: Int = this.textCacheMisses,
        phases: ScenePhaseStats = this.phases,
        p99FrameTimeMs: Float = this.p99FrameTimeMs,
        maxFrameTimeMs: Float = this.maxFrameTimeMs,
        render: RenderFrameStats? = this.render,
        uiUploads: UiUploadStats? = this.uiUploads,
    ): SceneFrameStats = copy(
        frameTimeMs, fps, trialPasses, textCacheHits, textCacheMisses, phases, p99FrameTimeMs, maxFrameTimeMs, render,
        uiUploads, passes, renderPasses, bufferCreations, resolution,
    )

    /**
     * Total number of text layout cache queries.
     */
    val textCacheTotal: Int get() = textCacheHits + textCacheMisses

    /**
     * Cache hit percentage for text layout lookups.
     */
    val textCacheHitRatePercent: Int get() = if (textCacheTotal > 0) (textCacheHits * 100 / textCacheTotal) else 0
}

/**
 * Breakdown of frame duration by architectural phase.
 *
 * All values reflect durations from the previous frame in milliseconds.
 *
 * @property uiBuildMs Time spent executing Compose UI composition and layout in milliseconds.
 * @property uiWaitMs Time spent blocked waiting for GPU resource allocation in milliseconds.
 * @property uiStageMs Time spent tessellating and staging UI render commands in milliseconds.
 * @property simRenderMs Time spent running simulation systems, scene extraction, and command submission in milliseconds.
 * @property trialMs Share of [uiBuildMs] spent inside speculative trial-measurement passes in milliseconds.
 * @property trialPasses Number of speculative trial measurement passes executed.
 * @property gameMs Share of [simRenderMs] spent executing ECS fixed and frame gameplay systems in milliseconds.
 * @property renderMs Share of [simRenderMs] spent executing infrastructure transform and render systems in milliseconds.
 */
data class ScenePhaseStats(
    val uiBuildMs: Float = 0f,
    val uiWaitMs: Float = 0f,
    val uiStageMs: Float = 0f,
    val simRenderMs: Float = 0f,
    val trialMs: Float = 0f,
    val trialPasses: Int = 0,
    val gameMs: Float = 0f,
    val renderMs: Float = 0f,
) {
    /**
     * Whether these numbers came from a frame that was actually timed.
     *
     * [SceneAppLifecycleRuntime.phaseStats] reports all-zero rather than null while collection is
     * off, so "not measured" and "measured, and free" are the same value. A display cannot tell
     * them apart, and Studio's status bar showed four permanent `0.0ms` readings because of it.
     * A real frame never costs nothing, so any non-zero phase means the timers ran.
     */
    val isMeasured: Boolean
        get() = uiBuildMs > 0f || uiWaitMs > 0f || uiStageMs > 0f || simRenderMs > 0f
}

/**
 * Captures and formats the current frame statistics from the active scene lifecycle runtime.
 *
 * @return A populated [SceneFrameStats] snapshot of the current frame execution.
 */
fun SceneAppLifecycleRuntime.frameStats(): SceneFrameStats {
    val phases = phaseStats()
    return SceneFrameStats(
        frameTimeMs = averageFrameTimeMs.roundToTenth(),
        fps = fps,
        trialPasses = phases.trialPasses,
        textCacheHits = 0,
        textCacheMisses = 0,
        phases = phases,
        p99FrameTimeMs = frameSpread.p99FrameTimeMs.roundToTenth(),
        maxFrameTimeMs = frameSpread.maxFrameTimeMs.roundToTenth(),
        render = rendererFrameStats,
        uiUploads = rendererUiUploads,
        passes = rendererPassStats,
        renderPasses = rendererRenderPasses,
        bufferCreations = rendererBufferCreations,
        resolution = renderQuality?.dynamicResolutionStats,
    )
}

private fun Float.roundToTenth(): Float = (this * 10f).roundToInt() / 10f
