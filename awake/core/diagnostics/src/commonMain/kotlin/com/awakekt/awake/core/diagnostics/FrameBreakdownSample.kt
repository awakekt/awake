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
 */
data class FrameBreakdownSample(
    val phases: FramePhaseSample = FramePhaseSample(),
    val render: FrameRenderSample? = null,
)

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
