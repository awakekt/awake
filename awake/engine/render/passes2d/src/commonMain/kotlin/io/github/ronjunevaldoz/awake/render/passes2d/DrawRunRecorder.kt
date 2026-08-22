// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes2d

/**
 * The backend hooks [io.github.ronjunevaldoz.awake.render.passes2d.SharedRenderFeature2D] needs:
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

    /** Binds and draws [mesh] against the pipeline [bindPipeline] just prepared. */
    fun drawMesh(mesh: M)

    /** Sets the pass scissor rect. Already clamped to the surface by the caller. */
    fun setScissor(x: Int, y: Int, width: Int, height: Int)

    /**
     * Uploads and draws one textured primitive.
     */
    fun drawTexturePrimitive(primitive: TexturedDrawRun, slotIndex: Int)
}

typealias UiRunRecorder<M> = DrawRunRecorder<M>
