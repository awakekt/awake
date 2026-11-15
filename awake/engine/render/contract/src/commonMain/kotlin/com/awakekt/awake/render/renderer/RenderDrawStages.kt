/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/** A part of [Renderer.draw] a profile can tell apart; see [RenderDrawStages]. */
enum class RenderDrawStage {
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
