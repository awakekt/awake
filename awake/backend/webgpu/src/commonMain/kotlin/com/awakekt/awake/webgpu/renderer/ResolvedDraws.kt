/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.webgpu.pipeline.WebGpuBindGroupHandle

/**
 * [this] draw as the canonical packet, its depth-pass fields given here: built once a draw.
 *
 * The preparer learns a draw's depth pass only after preparing it, and built the packet first and
 * then a `copy` with those fields: two packets a draw, every draw of every moving frame, about a
 * quarter of the WebGPU backend's garbage on a moving camera (#1555).
 */
internal fun WebGpuPreparedDraw.resolved(
    depthRenderKey: DepthRenderKey? = null,
    alphaCutoff: Float = this.alphaCutoff,
    depthPipeline: PipelineHandle? = this.depthPipeline,
    depthMaterialBinding: MaterialBinding? = this.depthMaterialBinding,
    depthJointPaletteBinding: MaterialBinding? = this.depthJointPaletteBinding,
): GpuResolvedDraw = GpuResolvedDraw(
    pipeline = pipeline,
    vertexFormat = vertexFormat,
    depthPipeline = depthPipeline,
    depthMaterialBinding = depthMaterialBinding,
    depthJointPaletteBinding = depthJointPaletteBinding,
    depthRenderKey = depthRenderKey,
    alphaCutoff = alphaCutoff,
    materialBinding = materialBinding,
    vertexBuffer = vertexBuffer,
    indexBuffer = indexBuffer,
    elementCount = elementCount,
    transparent = transparent,
    depthSortKey = depthSortKey,
    sortOrder = sortOrder,
    batchKey = batchKey,
    instances = instances,
    instanceVertexBuffer = instanceVertexBuffer,
    jointPaletteBinding = jointPaletteBinding,
    shadowBinding = shadowBinding,
    sceneDepthBinding = sceneDepthBinding,
    instanceColorBuffer = instanceColorBuffer,
    instanceFrameBuffer = instanceFrameBuffer,
    sortLayer = sortLayer,
)

/**
 * The last packet each stable draw slot resolved to, handed back while the draw in that slot would
 * resolve to the same one: the same handles and values, field by field (#1555).
 *
 * A moving camera prepares every draw every frame, and each built a packet, about half of what a
 * prepared draw allocated; the packets a static prop resolves to hold still. Packets are immutable,
 * so one handed back again is as good as a new one. Keyed by slot, which [StableDrawSlots] keeps for
 * the same draw frame after frame; single, instanced and edge draws number their slots apart.
 */
internal class ResolvedDrawCache {
    private val bySlot = Array(KINDS) { ArrayList<GpuResolvedDraw?>() }

    /** Packets handed back and packets built, over the cache's life: for tests and probes. */
    var reused = 0L
        private set
    var built = 0L
        private set

    @Suppress("LongParameterList")
    fun resolve(
        kind: Int,
        slot: Int,
        draw: WebGpuPreparedDraw,
        depthRenderKey: DepthRenderKey? = null,
        alphaCutoff: Float = draw.alphaCutoff,
        depthPipeline: PipelineHandle? = draw.depthPipeline,
        depthMaterialBinding: MaterialBinding? = draw.depthMaterialBinding,
        depthJointPaletteBinding: MaterialBinding? = draw.depthJointPaletteBinding,
    ): GpuResolvedDraw {
        val slots = bySlot[kind]
        while (slots.size <= slot) slots += null
        val cached = slots[slot]
        val same = cached?.holdsDepth(depthRenderKey, alphaCutoff, depthPipeline, depthMaterialBinding, depthJointPaletteBinding)
        if (cached != null && same == true && cached.holds(draw)) {
            reused += 1
            return cached
        }
        built += 1
        return draw.resolved(depthRenderKey, alphaCutoff, depthPipeline, depthMaterialBinding, depthJointPaletteBinding)
            .also { slots[slot] = it }
    }

    companion object {
        const val SINGLE = 0
        const val INSTANCED = 1
        const val EDGE = 2
        private const val KINDS = 3

        /** Which slots a draw numbers in: its edges', its instanced pool's, or its single pool's. */
        fun kindOf(edges: Boolean, instanced: Boolean): Int = if (edges) {
            EDGE
        } else if (instanced) {
            INSTANCED
        } else {
            SINGLE
        }
    }
}

/** Whether this packet carries these depth-pass fields. */
private fun GpuResolvedDraw.holdsDepth(
    depthRenderKey: DepthRenderKey?,
    alphaCutoff: Float,
    depthPipeline: PipelineHandle?,
    depthMaterialBinding: MaterialBinding?,
    depthJointPaletteBinding: MaterialBinding?,
): Boolean = this.depthRenderKey == depthRenderKey && this.alphaCutoff == alphaCutoff && this.depthPipeline === depthPipeline &&
    sameBinding(this.depthMaterialBinding, depthMaterialBinding) && sameBinding(this.depthJointPaletteBinding, depthJointPaletteBinding)

/** Whether this packet carries [draw]'s own fields: handles by identity, values by equality. */
private fun GpuResolvedDraw.holds(draw: WebGpuPreparedDraw): Boolean = holdsBuffers(draw) && holdsBindings(draw) && holdsOrder(draw)

private fun GpuResolvedDraw.holdsBuffers(draw: WebGpuPreparedDraw): Boolean =
    pipeline === draw.pipeline && vertexFormat == draw.vertexFormat && vertexBuffer === draw.vertexBuffer &&
        indexBuffer === draw.indexBuffer && elementCount == draw.elementCount && instances == draw.instances &&
        instanceVertexBuffer === draw.instanceVertexBuffer && instanceColorBuffer === draw.instanceColorBuffer &&
        instanceFrameBuffer === draw.instanceFrameBuffer

private fun GpuResolvedDraw.holdsBindings(draw: WebGpuPreparedDraw): Boolean =
    sameBinding(materialBinding, draw.materialBinding) && sameBinding(jointPaletteBinding, draw.jointPaletteBinding) &&
        sameBinding(shadowBinding, draw.shadowBinding) && sameBinding(sceneDepthBinding, draw.sceneDepthBinding)

private fun GpuResolvedDraw.holdsOrder(draw: WebGpuPreparedDraw): Boolean =
    transparent == draw.transparent && depthSortKey == draw.depthSortKey && sortOrder == draw.sortOrder &&
        batchKey == draw.batchKey && sortLayer == draw.sortLayer

/** The same binding: one object, or two wrappers a textured draw makes afresh each frame around one group. */
private fun sameBinding(a: MaterialBinding?, b: MaterialBinding?): Boolean =
    a === b || (a is WebGpuBindGroupHandle && b is WebGpuBindGroupHandle && a.bindGroup === b.bindGroup)
