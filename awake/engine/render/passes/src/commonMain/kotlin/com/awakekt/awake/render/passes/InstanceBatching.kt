/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.CullMode

/**
 * [drawCalls] with every set of draws that differ only in their model matrix folded into one
 * instanced draw, for the formats [canInstance] accepts.
 *
 * A scene of repeated props issues one draw per copy, and each costs a uniform upload, a
 * descriptor bind and a draw call per pass; drawn as instances the whole set costs one of each.
 * Only opaque, non-instanced draws fold, and back-culled ones only where the backend built a
 * back-culled instanced pipeline: there is no blended one, and a draw that is already instanced
 * carries its own per-instance data. A
 * draw with no partner is left as it is. Groups come first, in [InstanceGroupOrder]'s order; the
 * rest follow them in their own order, so culling one of them moves no group. An
 * already-instanced draw carrying more copies than one draw's buffer holds is split.
 */
internal fun batchInstances(
    drawCalls: List<RenderDrawCommand>,
    canInstance: (VertexFormat, CullMode) -> Boolean,
): List<RenderDrawCommand> = InstanceBatcher().batch(drawCalls, canInstance)

/**
 * Most copies one instanced draw carries: both backends' instance buffers hold 4096 matrices a
 * draw, so a larger set becomes several draws.
 */
internal const val MAX_BATCHED_INSTANCES = 4096

/** The same for a skinned instanced draw: both backends' palette buffers hold 256 a draw. */
internal const val MAX_SKINNED_BATCHED_INSTANCES = 256

/**
 * Adds [draw], split into several when it carries more instances than one draw's buffer holds.
 * An authored instance list of any length draws; the buffers stay the size they are.
 */
internal fun MutableList<RenderDrawCommand>.addSplitToCapacity(draw: RenderDrawCommand) {
    val models = draw.instanceModels
    val capacity = if (draw.instanceJointPalettes != null) MAX_SKINNED_BATCHED_INSTANCES else MAX_BATCHED_INSTANCES
    if (models == null || models.size <= capacity) {
        add(draw)
        return
    }
    for (from in models.indices step capacity) {
        val to = minOf(from + capacity, models.size)
        add(
            draw.copy(
                instanceModels = models.subList(from, to),
                instanceJointPalettes = draw.instanceJointPalettes?.subList(from, to),
                instanceColors = draw.instanceColors?.subList(from, to),
                instanceFrames = draw.instanceFrames?.subList(from, to),
            ),
        )
    }
}

internal fun RenderDrawCommand.foldsIntoInstances(canInstance: (VertexFormat, CullMode) -> Boolean): Boolean =
    instanceModels == null &&
        instanceJointPalettes == null &&
        !transparent &&
        canInstance(mesh.format, cullMode)

/**
 * Everything but the model matrix is equal, so the two render identically but for placement; with
 * [tints], also but for their tint and fade, which a tinting pipeline carries per instance.
 */
internal fun RenderDrawCommand.instancesWith(other: RenderDrawCommand, tints: Boolean = false): Boolean =
    mesh === other.mesh &&
        material === other.material &&
        extrasAlike(other, tints) &&
        vertexAnimation == other.vertexAnimation &&
        timeSeconds == other.timeSeconds &&
        alphaMode == other.alphaMode &&
        alphaCutoff == other.alphaCutoff &&
        shadowsOnly == other.shadowsOnly &&
        castsShadows == other.castsShadows &&
        maskLayer == other.maskLayer &&
        exemptFromCoverings == other.exemptFromCoverings &&
        (tints || fadeOpacity == other.fadeOpacity) &&
        cullMode == other.cullMode

/** Equal extras; with [tints], equal but for the tint a tinting pipeline carries per instance. */
private fun RenderDrawCommand.extrasAlike(other: RenderDrawCommand, tints: Boolean): Boolean =
    if (tints) InstanceTints.sameBesidesTint(extraUniformFloats, other.extraUniformFloats) else extraUniformFloats.contentEquals(other.extraUniformFloats)

/** A map key for [instancesWith]: draws with equal keys render identically but for placement. */
internal class InstanceKey(private val draw: RenderDrawCommand, private val tints: Boolean = false) {
    private val hash: Int = draw.instanceHash()

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean = other is InstanceKey && tints == other.tints && draw.instancesWith(other.draw, tints)
}

/** A hash equal for draws that [instancesWith] each other, tinted or not: the tint is left out of it. */
internal fun RenderDrawCommand.instanceHash(): Int =
    ((mesh.hashCode() * 31 + material.hashCode()) * 31 + InstanceTints.hashBesidesTint(extraUniformFloats)) * 31 + alphaMode.hashCode()

/** Which draws a [InstanceBatcher] folds, which of them render alike, and a hash equal for those that do. */
internal interface FoldRule {
    /** Whether a draw the rule does not fold is kept in the output as it is. */
    val keepsOthers: Boolean

    fun folds(draw: RenderDrawCommand, canInstance: (VertexFormat, CullMode) -> Boolean): Boolean

    /** Whether [first] and [second] draw alike; with [tints], their tint and fade may differ. */
    fun alike(first: RenderDrawCommand, second: RenderDrawCommand, tints: Boolean): Boolean

    fun hash(draw: RenderDrawCommand): Int
}

/** The scene pass's folding: draws that differ only in placement. */
internal object SceneInstanceFold : FoldRule {
    override val keepsOthers = true

    override fun folds(draw: RenderDrawCommand, canInstance: (VertexFormat, CullMode) -> Boolean) = draw.foldsIntoInstances(canInstance)

    override fun alike(first: RenderDrawCommand, second: RenderDrawCommand, tints: Boolean) = first.instancesWith(second, tints)

    override fun hash(draw: RenderDrawCommand) = draw.instanceHash()
}
