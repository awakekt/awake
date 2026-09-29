/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode

/**
 * [drawCalls] with every set of draws that differ only in their model matrix folded into one
 * instanced draw, for the formats [canInstance] accepts.
 *
 * A scene of repeated props issues one draw per copy, and each costs a uniform upload, a
 * descriptor bind and a draw call per pass; drawn as instances the whole set costs one of each.
 * Only opaque, unculled, non-instanced draws fold: an instanced pipeline has no back-culled or
 * blended companion, and a draw that is already instanced carries its own per-instance data. A
 * draw with no partner is left as it is. The rest keep their order; groups follow them.
 */
internal fun batchInstances(
    drawCalls: List<RenderDrawCommand>,
    canInstance: (VertexFormat) -> Boolean,
): List<RenderDrawCommand> {
    val groups = LinkedHashMap<InstanceKey, MutableList<RenderDrawCommand>>()
    val kept = ArrayList<RenderDrawCommand>(drawCalls.size)
    for (draw in drawCalls) {
        if (draw.foldsIntoInstances(canInstance)) groups.getOrPut(InstanceKey(draw)) { ArrayList() } += draw else kept += draw
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

private fun RenderDrawCommand.foldsIntoInstances(canInstance: (VertexFormat) -> Boolean): Boolean =
    instanceModels == null &&
        instanceJointPalettes == null &&
        !transparent &&
        cullMode == CullMode.None &&
        canInstance(mesh.format)

private fun instancedDraw(members: List<RenderDrawCommand>): RenderDrawCommand {
    val first = members[0]
    return first.copy(
        model = Mat4(),
        instanceModels = members.map { it.model },
        worldBounds = members.fold(first.worldBounds) { bounds, member ->
            member.worldBounds?.let { bounds?.union(it) }
        },
    )
}

/** Everything but the model matrix: draws equal in all of it render identically but for placement. */
private class InstanceKey(draw: RenderDrawCommand) {
    private val mesh: GpuMesh = draw.mesh
    private val material: GpuMaterial = draw.material
    private val extras: FloatArray = draw.extraUniformFloats
    private val vertexAnimation: Vec3f = draw.vertexAnimation
    private val timeSeconds: Float = draw.timeSeconds
    private val alphaMode: AlphaMode = draw.alphaMode
    private val alphaCutoff: Float = draw.alphaCutoff
    private val shadowsOnly: Boolean = draw.shadowsOnly
    private val hash: Int = ((mesh.hashCode() * 31 + material.hashCode()) * 31 + extras.contentHashCode()) * 31 +
        alphaMode.hashCode()

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean = other is InstanceKey &&
        mesh === other.mesh &&
        material === other.material &&
        extras.contentEquals(other.extras) &&
        vertexAnimation == other.vertexAnimation &&
        timeSeconds == other.timeSeconds &&
        alphaMode == other.alphaMode &&
        alphaCutoff == other.alphaCutoff &&
        shadowsOnly == other.shadowsOnly
}
