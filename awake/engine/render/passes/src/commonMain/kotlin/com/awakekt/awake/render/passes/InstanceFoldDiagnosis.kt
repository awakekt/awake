/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode

/**
 * Counts into [stats] what [batchInstances] does with [drawCalls] given the same capabilities:
 * the instanced draws it folds, and for each scene draw it leaves on its own, why.
 *
 * Draws group as [batchInstances] groups them. A group of copies that cannot fold misses for the
 * capability that stopped it: blending, masking, its format, its cull mode, or skinning. A draw
 * with no copy at all misses as [InstanceFoldMiss.SingleCopy], or, when other draws share its mesh
 * but not its material or uniforms, as [InstanceFoldMiss.DistinctMaterial] or
 * [InstanceFoldMiss.DistinctUniforms]: the fix there is sharing, not a pipeline. Shadow-only draws
 * are not main-pass draws and are not counted.
 *
 * Runs only when a host collects stats, so it may allocate.
 */
internal fun diagnoseInstanceFolds(
    drawCalls: List<RenderDrawCommand>,
    canInstance: (VertexFormat, CullMode) -> Boolean,
    stats: SceneDrawStats,
) {
    stats.recordSource(drawCalls.size)
    val groups = foldGroups(drawCalls, canInstance, stats)
    val keysByMesh = HashMap<GpuMesh, Int>()
    val keysByMeshMaterial = HashMap<MeshMaterial, Int>()
    for (members in groups.values) {
        val first = members[0]
        keysByMesh[first.mesh] = (keysByMesh[first.mesh] ?: 0) + 1
        val pair = MeshMaterial(first.mesh, first.material)
        keysByMeshMaterial[pair] = (keysByMeshMaterial[pair] ?: 0) + 1
    }
    for ((group, members) in groups) {
        val first = members[0]
        val miss = group.miss
        when {
            members.size > 1 && miss != null -> stats.recordMiss(miss, members.size)
            members.size > 1 -> {
                val capacity = if (first.mesh.format.isSkinned) MAX_SKINNED_BATCHED_INSTANCES else MAX_BATCHED_INSTANCES
                stats.recordFold((members.size + capacity - 1) / capacity, members.size)
            }
            (keysByMeshMaterial[MeshMaterial(first.mesh, first.material)] ?: 0) > 1 -> stats.recordMiss(InstanceFoldMiss.DistinctUniforms)
            (keysByMesh[first.mesh] ?: 0) > 1 -> stats.recordMiss(InstanceFoldMiss.DistinctMaterial)
            else -> stats.recordMiss(InstanceFoldMiss.SingleCopy)
        }
    }
}

/**
 * The main-pass draws of [drawCalls] in the groups [batchInstances] makes, keyed by what stops the
 * group folding as well, null when nothing does: batching sets the draws that cannot fold aside
 * before it groups, so a blended copy never joins an opaque group. Authored instanced draws are
 * counted into [stats] and left out, as are shadow-only ones.
 */
private fun foldGroups(
    drawCalls: List<RenderDrawCommand>,
    canInstance: (VertexFormat, CullMode) -> Boolean,
    stats: SceneDrawStats,
): Map<FoldGroup, List<RenderDrawCommand>> {
    val groups = LinkedHashMap<FoldGroup, MutableList<RenderDrawCommand>>()
    for (draw in drawCalls) {
        if (draw.instanceModels != null) stats.recordAuthoredInstanced()
        if (draw.instanceModels != null || draw.shadowsOnly) continue
        val miss = if (draw.foldsIntoInstances(canInstance)) null else draw.capabilityMiss(canInstance)
        groups.getOrPut(FoldGroup(miss, InstanceKey(draw))) { ArrayList() } += draw
    }
    return groups
}

/** What stops copies of this draw folding, when [canInstance] or skinning is what does. */
private fun RenderDrawCommand.capabilityMiss(canInstance: (VertexFormat, CullMode) -> Boolean): InstanceFoldMiss {
    val format = mesh.format
    return when {
        transparent -> InstanceFoldMiss.Blended
        format.isSkinned -> InstanceFoldMiss.SkinnedNotInstanced
        alphaMode == AlphaMode.Masked -> InstanceFoldMiss.Masked
        CullMode.entries.any { it != cullMode && canInstance(format, it) } -> InstanceFoldMiss.CullMode
        else -> InstanceFoldMiss.VertexFormat
    }
}

private data class MeshMaterial(val mesh: GpuMesh, val material: GpuMaterial)

/** Draws [batchInstances] would put in one group: equal [key]s, and the same reason, if any, not to fold. */
private data class FoldGroup(val miss: InstanceFoldMiss?, val key: InstanceKey)
