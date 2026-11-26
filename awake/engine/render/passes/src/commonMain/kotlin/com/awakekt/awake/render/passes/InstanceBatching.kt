/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
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

internal fun batchInstances(
    drawCalls: List<RenderDrawCommand>,
    canInstance: (VertexFormat, CullMode) -> Boolean,
    canInstanceSkinned: (VertexFormat, CullMode) -> Boolean,
): List<RenderDrawCommand> = InstanceBatcher().batch(
    drawCalls = drawCalls,
    canInstance = canInstance,
    canInstanceSkinned = canInstanceSkinned,
)

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
        vertexAnimation.sameAnimation(other.vertexAnimation) &&
        timeSeconds == other.timeSeconds &&
        sameRenderState(other, tints)

internal fun RenderDrawCommand.sameRenderState(other: RenderDrawCommand, tints: Boolean): Boolean =
    alphaMode == other.alphaMode &&
        alphaCutoff == other.alphaCutoff &&
        shadowsOnly == other.shadowsOnly &&
        castsShadows == other.castsShadows &&
        maskLayer == other.maskLayer &&
        exemptFromCoverings == other.exemptFromCoverings &&
        (tints || fadeOpacity == other.fadeOpacity) &&
        surfaceDepositScale == other.surfaceDepositScale &&
        surfaceDepositSeed == other.surfaceDepositSeed &&
        cullMode == other.cullMode

/**
 * The same vertex animation: the same object, or equal components. Not `==`: `Vec3f` is a data class,
 * and on Kotlin/Wasm `==` on two distinct instances allocated on every call. A scene gives each
 * renderer its own animation vector, so folding 3,000 of them cost 157 B a renderer (#1621).
 *
 * Unlike data-class `equals`, the components compare as IEEE floats: a NaN component never matches
 * another vector's (only the same instance folds), and `-0f` matches `0f`.
 */
internal fun Vec3f.sameAnimation(other: Vec3f): Boolean = this === other || (x == other.x && y == other.y && z == other.z)

/** Equal extras; with [tints], equal but for the tint a tinting pipeline carries per instance. */
private fun RenderDrawCommand.extrasAlike(other: RenderDrawCommand, tints: Boolean): Boolean =
    if (tints) InstanceTints.sameBesidesTint(extraUniformFloats, other.extraUniformFloats) else extraUniformFloats.contentEquals(other.extraUniformFloats)

/** A map key for [instancesWith] (or [SkinnedInstanceFold.alike] when [skinned]): equal keys render alike. */
internal class InstanceKey(
    private val draw: RenderDrawCommand,
    private val tints: Boolean = false,
    private val skinned: Boolean = false,
) {
    private val hash: Int = if (skinned) SkinnedInstanceFold.hash(draw) else draw.instanceHash()

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean = other is InstanceKey &&
        skinned == other.skinned &&
        tints == other.tints &&
        if (skinned) SkinnedInstanceFold.alike(draw, other.draw) else draw.instancesWith(other.draw, tints)
}

/** A hash equal for draws that [instancesWith] each other, tinted or not: the tint is left out of it. */
internal fun RenderDrawCommand.instanceHash(): Int =
    ((mesh.hashCode() * 31 + material.hashCode()) * 31 + InstanceTints.hashBesidesTint(extraUniformFloats)) * 31 + alphaMode.hashCode()

/** Which draws a [InstanceBatcher] folds, which of them render alike, and a hash equal for those that do. */
internal interface FoldRule {
    /** Whether a draw the rule does not fold is kept in the output as it is. */
    val keepsOthers: Boolean

    fun folds(draw: RenderDrawCommand, instances: Boolean): Boolean

    /** Whether [draw] folds as a single posed skinned draw when [skinnedInstances] is true. */
    fun foldsSkinned(draw: RenderDrawCommand, skinnedInstances: Boolean): Boolean = false

    /** Whether [first] and [second] draw alike; with [tints], their tint and fade may differ. */
    fun alike(first: RenderDrawCommand, second: RenderDrawCommand, tints: Boolean): Boolean

    /** Whether two single posed skinned draws can share one skinned-instanced batch. */
    fun alikeSkinned(first: RenderDrawCommand, second: RenderDrawCommand): Boolean = false

    fun hash(draw: RenderDrawCommand): Int

    /** Hash equal for two single skinned draws accepted by [alikeSkinned]. */
    fun hashSkinned(draw: RenderDrawCommand): Int = hash(draw)
}

internal fun FoldRule.matches(first: RenderDrawCommand, second: RenderDrawCommand, tints: Boolean, skinned: Boolean): Boolean =
    if (skinned) alikeSkinned(first, second) else alike(first, second, tints)

internal fun FoldRule.hashFor(draw: RenderDrawCommand, skinned: Boolean): Int =
    if (skinned) hashSkinned(draw) else hash(draw)

/** The scene pass's folding: draws that differ only in placement, plus posed skinned copies. */
internal object SceneInstanceFold : FoldRule {
    override val keepsOthers = true

    override fun folds(draw: RenderDrawCommand, instances: Boolean) =
        instances && draw.instanceModels == null && draw.instanceJointPalettes == null && !draw.transparent

    override fun foldsSkinned(draw: RenderDrawCommand, skinnedInstances: Boolean): Boolean =
        skinnedInstances && SkinnedInstanceFold.canFold(draw)

    override fun alike(first: RenderDrawCommand, second: RenderDrawCommand, tints: Boolean) = first.instancesWith(second, tints)

    override fun alikeSkinned(first: RenderDrawCommand, second: RenderDrawCommand): Boolean =
        SkinnedInstanceFold.alike(first, second)

    override fun hash(draw: RenderDrawCommand) = draw.instanceHash()

    override fun hashSkinned(draw: RenderDrawCommand): Int = SkinnedInstanceFold.hash(draw)
}
