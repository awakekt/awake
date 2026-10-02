/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.pipeline.CullMode
import kotlin.math.max
import kotlin.math.min

/**
 * [drawCalls] with every set of draws that differ only in their model matrix folded into one
 * instanced draw, for the formats [canInstance] accepts.
 *
 * A scene of repeated props issues one draw per copy, and each costs a uniform upload, a
 * descriptor bind and a draw call per pass; drawn as instances the whole set costs one of each.
 * Only opaque, unculled, non-instanced draws fold: an instanced pipeline has no back-culled or
 * blended companion, and a draw that is already instanced carries its own per-instance data. A
 * draw with no partner is left as it is. The rest keep their order; groups follow them. An
 * already-instanced draw carrying more copies than one draw's buffer holds is split.
 */
internal fun batchInstances(
    drawCalls: List<RenderDrawCommand>,
    canInstance: (VertexFormat) -> Boolean,
): List<RenderDrawCommand> {
    val groups = LinkedHashMap<InstanceKey, MutableList<RenderDrawCommand>>()
    val kept = ArrayList<RenderDrawCommand>(drawCalls.size)
    // Entities spawned together iterate together, so a draw usually joins the group the previous
    // one did. Checking that first skips building a key and hashing its uniform extras per draw.
    var lastDraw: RenderDrawCommand? = null
    var lastMembers: MutableList<RenderDrawCommand>? = null
    for (draw in drawCalls) {
        if (!draw.foldsIntoInstances(canInstance)) {
            kept.addSplitToCapacity(draw)
            continue
        }
        val members = lastMembers?.takeIf { lastDraw?.instancesWith(draw) == true }
            ?: groups.getOrPut(InstanceKey(draw)) { ArrayList() }
        members += draw
        lastDraw = draw
        lastMembers = members
    }
    for (members in groups.values) {
        if (members.size == 1) kept += members[0] else members.chunked(MAX_BATCHED_INSTANCES).mapTo(kept, ::instancedDraw)
    }
    return kept
}

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
private fun MutableList<RenderDrawCommand>.addSplitToCapacity(draw: RenderDrawCommand) {
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

private fun RenderDrawCommand.foldsIntoInstances(canInstance: (VertexFormat) -> Boolean): Boolean =
    instanceModels == null &&
        instanceJointPalettes == null &&
        !transparent &&
        cullMode == CullMode.None &&
        canInstance(mesh.format)

private fun instancedDraw(members: List<RenderDrawCommand>): RenderDrawCommand =
    members[0].copy(
        model = Mat4(),
        instanceModels = members.map { it.model },
        worldBounds = enclosingBounds(members),
    )

/**
 * One box around every member's bounds, or null when any member is unbounded: an unbounded copy
 * makes the whole draw unbounded. Accumulated in floats, so a batch of thousands builds one box.
 */
private fun enclosingBounds(members: List<RenderDrawCommand>): Aabb? {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var maxZ = -Float.MAX_VALUE
    for (index in members.indices) {
        val bounds = members[index].worldBounds ?: return null
        minX = min(minX, bounds.min.x)
        minY = min(minY, bounds.min.y)
        minZ = min(minZ, bounds.min.z)
        maxX = max(maxX, bounds.max.x)
        maxY = max(maxY, bounds.max.y)
        maxZ = max(maxZ, bounds.max.z)
    }
    return Aabb(Vec3f(minX, minY, minZ), Vec3f(maxX, maxY, maxZ))
}

/** Everything but the model matrix is equal, so the two render identically but for placement. */
private fun RenderDrawCommand.instancesWith(other: RenderDrawCommand): Boolean =
    mesh === other.mesh &&
        material === other.material &&
        extraUniformFloats.contentEquals(other.extraUniformFloats) &&
        vertexAnimation == other.vertexAnimation &&
        timeSeconds == other.timeSeconds &&
        alphaMode == other.alphaMode &&
        alphaCutoff == other.alphaCutoff &&
        shadowsOnly == other.shadowsOnly

/** A map key for [instancesWith]: draws with equal keys render identically but for placement. */
private class InstanceKey(private val draw: RenderDrawCommand) {
    private val hash: Int =
        ((draw.mesh.hashCode() * 31 + draw.material.hashCode()) * 31 + draw.extraUniformFloats.contentHashCode()) * 31 +
            draw.alphaMode.hashCode()

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean = other is InstanceKey && draw.instancesWith(other.draw)
}
