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

    /** [drawStats] to fill this frame, made on first use, or null, and dropped, while they are off. */
    internal fun drawStatsForFrame(): SceneDrawStats? {
        if (!drawStatsEnabled) {
            drawStats = null
            return null
        }
        return drawStats ?: SceneDrawStats().also { drawStats = it }
    }
}
