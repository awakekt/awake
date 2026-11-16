/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * A part of drawing a frame a profile can tell apart; see [RenderDrawStages]. [Collect] and
 * [Compile] are the engine's planning, which runs before [Renderer.draw] and is reported by the system
 * that plans the frame; the rest are the renderer's own, reported by [Renderer.draw].
 */
enum class RenderDrawStage {
    /** Gathering what the frame draws: the scene's features, extraction and culling. */
    Collect,

    /** Turning the gathered draws into the renderer's input: folding, sorting and each draw's preparation. */
    Compile,

    /** Acquiring the surface, resolving the scene's scale and ordering the draws to record. */
    Prepare,

    /** Writing the frame's uniforms and textures to the GPU before anything is recorded. */
    Upload,

    /** Encoding the frame's passes: depth, scene, post-processing and the UI overlay. */
    Record,

    /** Handing the encoded frame to the GPU queue and publishing the frame's stats. */
    Submit,
}

/**
 * Told around each [RenderDrawStage] of one [Renderer.draw], so a profile that reads the heap or the
 * clock at those marks can split the draw instead of seeing it whole. [begin] and [end] come in
 * pairs, in stage order, and never nest. Install with [Renderer.observeDrawStages].
 */
interface RenderDrawStages {
    /** [stage] is about to run. */
    fun begin(stage: RenderDrawStage)

    /** [stage] has run. */
    fun end(stage: RenderDrawStage)
}
