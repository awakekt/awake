/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Per-frame CPU, UI, and GPU readings consumed by [FrameBreakdown].
 *
 * Keeps [FrameBreakdown] independent of scene or renderer modules so headless tools, tests, and
 * custom hosts can feed phase and draw readings directly while `awake:scene:runtime` provides a
 * one-call scene binding.
 *
 * @property phases Per-phase CPU durations for the frame; ignored until [FramePhaseSample.isMeasured].
 * @property render GPU timing and geometry submission counts for the frame, or `null` when uncounted.
 * @property uiUploads What the frame wrote into UI meshes, or `null` when the renderer does not count it.
 * @property passes Where the frame's draws went, by part of the frame, or `null` when uncounted.
 * @property renderPasses Render passes the frame began, offscreen passes included, or `null` when uncounted.
 */
data class FrameBreakdownSample(
    val phases: FramePhaseSample = FramePhaseSample(),
    val render: FrameRenderSample? = null,
    val uiUploads: FrameUiUploadSample? = null,
    val passes: FramePassSample? = null,
    val renderPasses: Int? = null,
) {
    /** The constructor as released before [uiUploads], kept so compiled callers still link. */
    @Deprecated("Use the constructor with uiUploads.", level = DeprecationLevel.HIDDEN)
    constructor(
        phases: FramePhaseSample = FramePhaseSample(),
        render: FrameRenderSample? = null,
    ) : this(phases, render, null, null, null)

    /** The constructor as released before [passes] and [renderPasses], kept so compiled callers still link. */
    @Deprecated("Use the constructor with passes and renderPasses.", level = DeprecationLevel.HIDDEN)
    constructor(
        phases: FramePhaseSample = FramePhaseSample(),
        render: FrameRenderSample? = null,
        uiUploads: FrameUiUploadSample? = null,
    ) : this(phases, render, uiUploads, null, null)

    /** [copy] as released before [uiUploads]. */
    @Deprecated("Use copy with uiUploads.", level = DeprecationLevel.HIDDEN)
    fun copy(
        phases: FramePhaseSample = this.phases,
        render: FrameRenderSample? = this.render,
    ): FrameBreakdownSample = copy(phases, render, uiUploads, passes, renderPasses)

    /** [copy] as released before [passes] and [renderPasses]. */
    @Deprecated("Use copy with passes and renderPasses.", level = DeprecationLevel.HIDDEN)
    fun copy(
        phases: FramePhaseSample = this.phases,
        render: FrameRenderSample? = this.render,
        uiUploads: FrameUiUploadSample? = this.uiUploads,
    ): FrameBreakdownSample = copy(phases, render, uiUploads, passes, renderPasses)
}

/**
 * CPU phase durations for a single frame in milliseconds.
 *
 * @property simMs Duration of fixed and frame simulation systems in milliseconds.
 * @property renderSystemsMs Duration of transform and render extraction systems in milliseconds.
 * @property simRenderMs Total scene update duration (`simMs + renderSystemsMs` plus host overhead) in milliseconds.
 * @property uiBuildMs UI composition and layout duration in milliseconds.
 * @property uiStageMs UI tessellation and command staging duration in milliseconds.
 * @property uiWaitMs Time spent waiting on UI GPU resources in milliseconds.
 */
data class FramePhaseSample(
    val simMs: Float = 0f,
    val renderSystemsMs: Float = 0f,
    val simRenderMs: Float = 0f,
    val uiBuildMs: Float = 0f,
    val uiStageMs: Float = 0f,
    val uiWaitMs: Float = 0f,
) {
    /** Whether at least one phase timer recorded non-zero work during the frame. */
    val isMeasured: Boolean
        get() = uiBuildMs > 0f || uiWaitMs > 0f || uiStageMs > 0f || simRenderMs > 0f
}

/**
 * GPU pass timing and submission counts for a single frame.
 *
 * @property drawCalls Draw commands submitted across all passes in the frame.
 * @property instances Total instances drawn across all passes in the frame.
 * @property triangles Total triangles submitted across all passes in the frame.
 * @property gpuTimeMs Total GPU execution time in milliseconds, or `null` when device timing is unavailable.
 */
data class FrameRenderSample(
    val drawCalls: Int,
    val instances: Int,
    val triangles: Long,
    val gpuTimeMs: Float? = null,
)

/**
 * UI mesh data a single frame uploaded to the GPU. A still UI reads zero; a non-zero reading on a
 * frame where nothing on screen moved names UI geometry that was staged and uploaded again.
 *
 * @property bytes Vertex and index bytes written to UI meshes.
 * @property runs UI draw runs whose mesh was written.
 * @property triangles Triangles in those runs.
 */
data class FrameUiUploadSample(
    val bytes: Long,
    val runs: Int,
    val triangles: Long,
)

/**
 * Draws a single frame recorded, split by the part of the frame that recorded them; the parts add up
 * to [FrameRenderSample.drawCalls]. It tells what culling left and what shadow cascades add.
 *
 * @property sceneOpaqueDraws Draws of the scene pass's opaque geometry.
 * @property sceneTransparentDraws Blended draws of the scene pass.
 * @property shadowDraws Draws across every shadow depth pass.
 * @property shadowPasses Shadow depth passes drawn: directional cascades and point-shadow faces.
 * @property uiDraws Draws of the UI pass.
 * @property otherDraws Every other draw: the sky, debug lines, depth and mask passes, image passes.
 */
data class FramePassSample(
    val sceneOpaqueDraws: Int,
    val sceneTransparentDraws: Int,
    val shadowDraws: Int,
    val shadowPasses: Int,
    val uiDraws: Int,
    val otherDraws: Int,
)
