/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import kotlin.math.roundToInt

/** Same shape as [io.github.awakelab.awake.engine.application.GameFrameStats] -- see that
 * type's doc comment. */
data class SceneFrameStats(
    val frameTimeMs: Float,
    val fps: Float,
    val trialPasses: Int,
    val textCacheHits: Int,
    val textCacheMisses: Int,
    /** Phase attribution, zero unless [SceneAppLifecycleRuntime.perfStatsEnabled]. */
    val phases: ScenePhaseStats = ScenePhaseStats(),
) {
    val textCacheTotal: Int get() = textCacheHits + textCacheMisses
    val textCacheHitRatePercent: Int get() = if (textCacheTotal > 0) (textCacheHits * 100 / textCacheTotal) else 0
}

/**
 * Where a frame's milliseconds went, split at the three boundaries the runtime already has:
 * building the UI (layout, including every trial pass), staging its primitives for the GPU, and
 * the simulation + render pump that presents.
 *
 * Exists because "the frame is slow" is not actionable and the guesses are expensive: a UI
 * layout rewrite and a GPU submission fix are months apart in cost. All values are from the
 * previous frame -- an overlay reading them draws inside the UI phase, so a live read would
 * report a partial frame.
 */
data class ScenePhaseStats(
    val uiBuildMs: Float = 0f,
    /**
     * Time blocked waiting for the GPU to release this frame's resource slot.
     *
     * Split out of [uiStageMs] because it is not UI work: it measures how far behind the GPU is,
     * and folding it into the staging phase made a GPU-bound frame read as an expensive UI.
     */
    val uiWaitMs: Float = 0f,
    val uiStageMs: Float = 0f,
    val simRenderMs: Float = 0f,
    /** Share of [uiBuildMs] spent inside trial-measure passes specifically. */
    val trialMs: Float = 0f,
    val trialPasses: Int = 0,
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

fun SceneAppLifecycleRuntime.frameStats(): SceneFrameStats {
    val phases = phaseStats()
    return SceneFrameStats(
        frameTimeMs = averageFrameTimeMs.roundToTenth(),
        fps = fps,
        trialPasses = phases.trialPasses,
        textCacheHits = 0,
        textCacheMisses = 0,
        phases = phases,
    )
}

private fun Float.roundToTenth(): Float = (this * 10f).roundToInt() / 10f
