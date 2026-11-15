/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.debug

import com.awakekt.awake.render.passes.SceneDrawStats

/** Last scene-extraction counters for an in-app diagnostics surface. */
object RenderDiagnostics {
    /** Physical render-target width/height used for the current scene projection. */
    var surfaceAspect: Float = 16f / 9f
        internal set

    /** Number of draw calls eliminated by frustum culling. */
    var frustumCulled: Int = 0
        internal set

    /** Number of draw calls eliminated by occlusion culling. */
    var occluded: Int = 0
        internal set

    /** Total count of draw commands submitted for rendering. */
    var submittedDrawCalls: Int = 0
        internal set

    /** Total count of mesh instances submitted across all draw calls. */
    var submittedInstances: Int = 0
        internal set

    /** Draws extracted from the scene but rejected by the backend resource resolver. */
    var unresolvedDrawCalls: Int = 0
        internal set

    /**
     * Whether the scene's render system counts, each frame it plans, the draws each pass receives
     * and why main-pass draws did not fold into instances, into [drawStats]. Off by default, which
     * costs nothing; the scene runtime turns it on with its perf stats (F2).
     */
    var drawStatsEnabled: Boolean = false

    /**
     * The last planned frame's draws per pass and fold misses by reason, or null while
     * [drawStatsEnabled] is off or before a frame was planned with it on. One instance, rewritten
     * each frame it is on.
     */
    var drawStats: SceneDrawStats? = null
        internal set

    /**
     * The main view's blended draws by source in the last frame planned while [drawStatsEnabled] was
     * on: mesh renderers, particles and each render feature (#1621). Null while it is off.
     */
    var transparentSources: TransparentDrawSources? = null
        internal set

    private val captures = ArrayList<TransparentDrawSources>()
    private var capturesThisFrame = 0

    /**
     * The same as [transparentSources] for each offscreen view planned since the main view, such as a
     * canvas view's portrait, in the order they were planned. Empty while [drawStatsEnabled] is off.
     */
    val captureTransparentSources: List<TransparentDrawSources>
        get() = if (drawStatsEnabled) captures.subList(0, capturesThisFrame).toList() else emptyList()

    /** [transparentSources] to fill for the main view, starting a frame's captures afresh; null while off. */
    internal fun transparentSourcesForMain(featureNames: List<String>): TransparentDrawSources? {
        capturesThisFrame = 0
        if (!drawStatsEnabled) {
            transparentSources = null
            captures.clear()
            return null
        }
        return reused(transparentSources, featureNames).also { transparentSources = it }
    }

    /** The next capture's [TransparentDrawSources] this frame, kept for the next frame's; null while off. */
    internal fun transparentSourcesForCapture(featureNames: List<String>): TransparentDrawSources? {
        if (!drawStatsEnabled) return null
        val index = capturesThisFrame++
        val sources = reused(captures.getOrNull(index), featureNames)
        if (index < captures.size) captures[index] = sources else captures += sources
        return sources
    }

    /** [kept] while it names the same features, else a new one: another render system's planner names others. */
    private fun reused(kept: TransparentDrawSources?, featureNames: List<String>): TransparentDrawSources =
        kept?.takeIf { it.featureNames === featureNames } ?: TransparentDrawSources(featureNames)

    /** [drawStats] to fill this frame, made on first use, or null, and dropped, while they are off. */
    internal fun drawStatsForFrame(): SceneDrawStats? {
        if (!drawStatsEnabled) {
            drawStats = null
            return null
        }
        return drawStats ?: SceneDrawStats().also { drawStats = it }
    }
}
