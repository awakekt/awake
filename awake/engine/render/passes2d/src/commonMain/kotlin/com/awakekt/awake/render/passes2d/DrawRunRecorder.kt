/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.imagepass.ImagePassChain

/**
 * The backend hooks [com.awakekt.awake.render.passes2d.SharedRenderFeature2D] needs:
 * paint order, scissor clamping and texture-slot allocation live once in the shared feature, and
 * each method here is one backend's translation of a single step into real graphics calls.
 *
 * @param M The backend's 2D mesh type.
 */
interface DrawRunRecorder<in M> {
    /**
     * Prepares [kind]'s pipeline for the draws that follow.
     *
     * @return `false` when this frame never built that pipeline, in which case the shared loop
     * skips the run rather than drawing it through whatever was bound before.
     */
    fun bindPipeline(kind: DrawPipelineKind): Boolean

    /**
     * Binds [font]'s atlas and sampling uniforms for the glyph draws that follow, after
     * [bindPipeline] prepared the glyph pipeline. Called again only when the font changes, and
     * never rebuilds a pipeline: a backend uploads each atlas once and keeps it, keyed by font.
     *
     * @return `false` when [font] cannot be bound, in which case its runs are skipped.
     */
    fun bindGlyphFont(font: UiFont): Boolean

    /** Binds and draws [mesh] against the pipeline [bindPipeline] just prepared. */
    fun drawMesh(mesh: M)

    /** Sets the pass scissor rect. Already clamped to the surface by the caller. */
    fun setScissor(x: Int, y: Int, width: Int, height: Int)

    /**
     * Uploads and draws one textured primitive.
     */
    fun drawTexturePrimitive(primitive: TexturedDrawRun, slotIndex: Int)

    /**
     * Interrupts the active 2D render pass, executes [chain] against the current destination, and
     * resumes the 2D render pass with `loadOp = LOAD`.
     *
     * @param chain The multi-step image-pass chain to execute.
     * @param hasMoreImagePasses True when another [DrawRun.ImagePassRun] follows later in the same pass.
     */
    fun executeImagePass(chain: ImagePassChain, hasMoreImagePasses: Boolean) = Unit
}

typealias UiRunRecorder<M> = DrawRunRecorder<M>
