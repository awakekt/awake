// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.scene.runtime

import io.github.ronjunevaldoz.awake.ui.UiBoxConstraints
import io.github.ronjunevaldoz.awake.ui.api.layout.UiAlignment
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.context.UiMeasureTrialStats
import io.github.ronjunevaldoz.awake.ui.headless.UiScope
import io.github.ronjunevaldoz.awake.ui.headless.createUiScope
import io.github.ronjunevaldoz.awake.ui.foundation.text.textLayoutCacheStats
import io.github.ronjunevaldoz.awake.ui.layouts.BoxScope
import kotlin.math.roundToInt

/** Root-level full-viewport box for a [SceneAppLifecycleRuntime] overlay -- same shape as
 * [io.github.ronjunevaldoz.awake.engine.application.AppUiRuntime.frame], the port that closes
 * the `AppUiRuntime`/`SceneAppLifecycleRuntime` composability gap. Takes [viewportWidth]/
 * [viewportHeight] as explicit params (available from [SceneOverlayBlock]'s own signature)
 * instead of reading stored fields -- `SceneAppLifecycleRuntime` doesn't keep viewport size in a field
 * the way `AppUiRuntime` does. */
fun SceneAppLifecycleRuntime.frame(
    viewportWidth: Float,
    viewportHeight: Float,
    contentAlignment: UiAlignment = UiAlignment.TopStart,
    block: BoxScope.(constraints: UiBoxConstraints) -> Unit,
) {
    val rootSlot = Rectangle(0f, 0f, viewportWidth, viewportHeight)
    uiContext.createBox(
        slot = rootSlot,
        contentAlignment = contentAlignment,
    ).block(
        UiBoxConstraints(
            maxWidthPx = viewportWidth,
            maxHeightPx = viewportHeight,
        ),
    )
}

/**
 * Headless facade entry point for scene overlays.
 *
 * [frame] remains available for advanced Core layout authors, while ordinary component trees
 * should start here so the callback receives only the public Headless [UiScope].
 */
fun SceneAppLifecycleRuntime.headlessFrame(
    viewportWidth: Float,
    viewportHeight: Float,
    block: UiScope.() -> Unit,
) {
    uiContext.createUiScope(Rectangle(0f, 0f, viewportWidth, viewportHeight)).block()
}

/** Same shape as [io.github.ronjunevaldoz.awake.engine.application.GameFrameStats] -- see that
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
    val uiStageMs: Float = 0f,
    val simRenderMs: Float = 0f,
    /** Share of [uiBuildMs] spent inside trial-measure passes specifically. */
    val trialMs: Float = 0f,
    val trialPasses: Int = 0,
)

fun SceneAppLifecycleRuntime.frameStats(): SceneFrameStats {
    val (cacheHits, cacheMisses) = textLayoutCacheStats()
    val phases = phaseStats()
    return SceneFrameStats(
        frameTimeMs = averageFrameTimeMs.roundToTenth(),
        fps = fps,
        trialPasses = phases.trialPasses,
        textCacheHits = cacheHits,
        textCacheMisses = cacheMisses,
        phases = phases,
    )
}

private fun Float.roundToTenth(): Float = (this * 10f).roundToInt() / 10f
