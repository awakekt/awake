/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/**
 * Why a draw of the main pass was drawn on its own rather than folded into an instanced draw with
 * its copies. See [SceneDrawStats.misses].
 */
enum class InstanceFoldMiss {
    /** Nothing else in the frame draws its mesh: there is no copy to fold it with. */
    SingleCopy,

    /** Drawn blended: there is no blended instanced pipeline, and blended draws keep their order. */
    Blended,

    /** Alpha-masked, and the backend has no instanced pipeline for its format and cull mode. */
    Masked,

    /** The backend has no instanced pipeline for its vertex format. */
    VertexFormat,

    /** The backend can instance its vertex format, but not with its cull mode. */
    CullMode,

    /** Other draws share its mesh, each with a material instance of its own. */
    DistinctMaterial,

    /** Other draws share its mesh and material, but its uniforms or draw state differ from theirs. */
    DistinctUniforms,

    /** A posed skinned mesh whose format the backend cannot draw as skinned instances. */
    SkinnedNotInstanced,
}

/**
 * What the scene compiler handed each pass of one frame, and why main-pass draws did not fold
 * into instances. Filled by [ScenePassCompiler.compile] when a host passes one, and left alone
 * otherwise, so collecting costs nothing while it is off.
 *
 * Counts are of the draws the compiler prepared, after instancing folded copies together: an
 * instanced draw of 400 copies is one draw. They are what the backend records for those passes,
 * less any draw its pipeline table cannot record; [com.awakekt.awake.render.renderer.RenderFrameStats]
 * is the backend's own total over every pass, image passes and UI included.
 *
 * One instance is reused frame after frame; read it between frames.
 */
class SceneDrawStats {
    private val missCounts = IntArray(InstanceFoldMiss.entries.size)
    private var shadowPassDrawCounts = IntArray(INITIAL_SHADOW_PASSES)

    /** Draw requests the scene submitted, before folding. Each authored instanced draw is one. */
    var sourceDraws: Int = 0
        private set

    /** Opaque draws in the main pass. */
    var opaqueDraws: Int = 0
        private set

    /** Blended draws in the main pass. */
    var transparentDraws: Int = 0
        private set

    /** Main-pass draws: [opaqueDraws] and [transparentDraws]. */
    val mainPassDraws: Int get() = opaqueDraws + transparentDraws

    /** Instanced draws folding made from copies the scene submitted one by one. */
    var foldedDraws: Int = 0
        private set

    /** The source draws those [foldedDraws] carry between them. */
    var foldedCopies: Int = 0
        private set

    /** Draws the scene submitted already instanced, which fold no further. */
    var authoredInstancedDraws: Int = 0
        private set

    /** Shadow depth passes: one per directional cascade, then one per point-shadow cube face. */
    var shadowPasses: Int = 0
        private set

    /** Draws across every shadow pass. */
    var shadowDraws: Int = 0
        private set

    /** Draws across the mask passes, such as an outline's. */
    var maskDraws: Int = 0
        private set

    /** Wireframe edge draws over the frame. */
    var edgeDraws: Int = 0
        private set

    /** Draws in the shadow pass at [index], cascades first; 0 past [shadowPasses]. */
    fun shadowPassDraws(index: Int): Int = if (index in 0 until shadowPasses) shadowPassDrawCounts[index] else 0

    /** Main-pass draws that stayed apart from their copies for [reason]. */
    fun misses(reason: InstanceFoldMiss): Int = missCounts[reason.ordinal]

    /** Main-pass draws that stayed apart for any reason. */
    val totalMisses: Int get() = missCounts.sum()

    internal fun reset() {
        missCounts.fill(0)
        sourceDraws = 0
        opaqueDraws = 0
        transparentDraws = 0
        foldedDraws = 0
        foldedCopies = 0
        authoredInstancedDraws = 0
        shadowPasses = 0
        shadowDraws = 0
        maskDraws = 0
        edgeDraws = 0
    }

    internal fun recordSource(draws: Int) {
        sourceDraws = draws
    }

    internal fun recordMiss(reason: InstanceFoldMiss, draws: Int = 1) {
        missCounts[reason.ordinal] += draws
    }

    internal fun recordFold(draws: Int, copies: Int) {
        foldedDraws += draws
        foldedCopies += copies
    }

    internal fun recordAuthoredInstanced() {
        authoredInstancedDraws += 1
    }

    internal fun recordMainPass(opaque: Int, transparent: Int, mask: Int, edges: Int) {
        opaqueDraws = opaque
        transparentDraws = transparent
        maskDraws = mask
        edgeDraws = edges
    }

    internal fun recordShadowPass(draws: Int) {
        if (shadowPasses == shadowPassDrawCounts.size) shadowPassDrawCounts = shadowPassDrawCounts.copyOf(shadowPasses * 2)
        shadowPassDrawCounts[shadowPasses++] = draws
        shadowDraws += draws
    }

    override fun toString(): String = buildString {
        append("SceneDrawStats(source=").append(sourceDraws)
        append(", opaque=").append(opaqueDraws).append(", transparent=").append(transparentDraws)
        append(", folded=").append(foldedDraws).append(" of ").append(foldedCopies)
        append(", shadow=").append(shadowDraws).append(" in ").append(shadowPasses)
        append(", mask=").append(maskDraws)
        InstanceFoldMiss.entries.forEach { reason ->
            if (misses(reason) > 0) append(", ").append(reason.name).append('=').append(misses(reason))
        }
        append(')')
    }
}

/** Shadow passes a frame usually has: the directional cascades. More grow the array. */
private const val INITIAL_SHADOW_PASSES = 4
