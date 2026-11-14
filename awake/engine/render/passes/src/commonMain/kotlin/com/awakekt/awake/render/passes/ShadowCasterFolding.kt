/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode

/**
 * The scene's casters folded again for the shadow passes, by what a depth pass draws alone.
 *
 * A depth pass writes no colour, so copies of one mesh and material that the scene pass keeps
 * apart for their tint, fade or other per-draw factors cast alike: drawn as one instanced caster
 * each, every shadow pass costs a draw per mesh rather than one per tint. Without this a town of
 * 190 meshes whose props each carry their own tint cast 1,330 draws into every cascade.
 *
 * Only opaque, unanimated-palette draws the backend can instance fold; the rest cast as they are.
 */
internal class ShadowCasterFolding {
    private val batcher = InstanceBatcher(DepthCasterFold)
    private var folded = BooleanArray(INITIAL_REQUESTS)

    private var casters: List<RenderDrawCommand> = emptyList()

    /**
     * Folds [requests], the scene pass's folded draws, into shadow-only instanced casters when
     * [enabled]. Each request folded into one is marked, so [castsAlone] answers false for it; the
     * scene still draws it. Valid until the next call.
     */
    fun fold(requests: List<RenderDrawCommand>, enabled: Boolean, canInstance: (VertexFormat, CullMode) -> Boolean) {
        if (folded.size < requests.size) folded = BooleanArray(requests.size * 2)
        folded.fill(false, 0, requests.size)
        casters = if (enabled) batcher.batch(requests, canInstance) else emptyList()
        if (!enabled) return
        batcher.forEachFolded { folded[it] = true }
        for (index in 0 until casters.size) {
            casters[index].shadowsOnly = true
            casters[index].castsShadows = true
        }
    }

    /** Prepares the last [fold]'s casters from source index [firstIndex] on, handing each to [add] with its bounds. */
    inline fun prepare(preparer: GpuDrawPreparer, firstIndex: Int, context: GpuDrawPreparationContext, add: (GpuResolvedDraw, Aabb?) -> Unit) {
        for (index in 0 until casters.size) {
            val caster = casters[index]
            preparer.prepare(caster, firstIndex + index, context)?.let { add(it, caster.worldBounds) }
        }
    }

    /** Whether the request at [index] of the last [fold] casts as itself, rather than inside a folded caster. */
    fun castsAlone(index: Int): Boolean = !folded[index]

    private companion object {
        const val INITIAL_REQUESTS = 256
    }
}

/**
 * What a shadow depth pass of an opaque draw reads: the mesh, its material's depth binding, the
 * cull mode and the vertex animation. Not the extras, tint or fade the colour pass reads.
 */
private object DepthCasterFold : FoldRule {
    override val keepsOthers = false

    override fun folds(draw: RenderDrawCommand, canInstance: (VertexFormat, CullMode) -> Boolean): Boolean =
        !draw.transparent &&
            draw.castsShadows &&
            draw.alphaMode == AlphaMode.Opaque &&
            draw.instanceJointPalettes == null &&
            draw.instanceColors == null &&
            draw.instanceFrames == null &&
            !draw.mesh.format.isSkinned &&
            canInstance(draw.mesh.format, draw.cullMode)

    override fun alike(first: RenderDrawCommand, second: RenderDrawCommand, tints: Boolean): Boolean =
        first.mesh === second.mesh &&
            first.material === second.material &&
            first.cullMode == second.cullMode &&
            first.vertexAnimation == second.vertexAnimation &&
            first.timeSeconds == second.timeSeconds

    override fun hash(draw: RenderDrawCommand): Int = draw.mesh.hashCode() * 31 + draw.material.hashCode()
}
