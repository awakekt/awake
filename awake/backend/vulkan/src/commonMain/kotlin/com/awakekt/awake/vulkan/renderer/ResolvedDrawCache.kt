/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.toGpuResolvedDraw
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.depthSortKey

/**
 * The last packet each draw resolved to, handed back while the draw would resolve to the same one,
 * field by field (#1742), as WebGPU's cache does (#1555).
 *
 * A moving camera or a moving model prepares every draw every frame, and each built a
 * [GpuResolvedDraw], about 100 bytes a draw a frame. What moves is in the draw's uniforms, not its
 * packet: its buffers, bindings and order stay the same, an opaque draw's sort key included, which is
 * 0 (only transparent draws are ordered by distance). Packets are immutable, so one handed back again
 * is as good as a new one, and a still frame the pass keeps drawing holds the same objects.
 *
 * Keyed by kind, frame in flight (a draw binds its frame slot's descriptor set and buffers) and source
 * index. A key that lands on another draw only misses: every field is compared before a packet is
 * handed back.
 */
internal class ResolvedDrawCache {
    private val byKey = ArrayList<ArrayList<GpuResolvedDraw?>>()

    /** Packets handed back and packets built, over the cache's life: for tests and probes. */
    var reused = 0L
        private set
    var built = 0L
        private set

    @Suppress("LongParameterList")
    fun resolve(
        kind: Int,
        frameIndex: Int,
        index: Int,
        draw: PreparedDraw,
        depthRenderKey: DepthRenderKey? = null,
        alphaCutoff: Float = draw.alphaCutoff,
        depthPipeline: PipelineHandle? = draw.depthPipeline,
        depthMaterialBinding: MaterialBinding? = draw.depthMaterialBinding,
        depthJointPaletteBinding: MaterialBinding? = draw.depthJointPaletteBinding,
    ): GpuResolvedDraw {
        val slots = slotsFor(frameIndex * KINDS + kind)
        while (slots.size <= index) slots += null
        val cached = slots[index]
        if (cached != null &&
            cached.holdsDepth(depthRenderKey, alphaCutoff, depthPipeline, depthMaterialBinding, depthJointPaletteBinding) &&
            cached.holds(draw)
        ) {
            reused += 1
            return cached
        }
        built += 1
        return draw.toGpuResolvedDraw(depthRenderKey, alphaCutoff, depthPipeline, depthMaterialBinding, depthJointPaletteBinding)
            .also { slots[index] = it }
    }

    private fun slotsFor(key: Int): ArrayList<GpuResolvedDraw?> {
        while (byKey.size <= key) byKey += ArrayList<GpuResolvedDraw?>()
        return byKey[key]
    }

    companion object {
        const val SINGLE = 0
        const val EDGE = 1
        private const val KINDS = 2
    }
}

private fun GpuResolvedDraw.holdsDepth(
    depthRenderKey: DepthRenderKey?,
    alphaCutoff: Float,
    depthPipeline: PipelineHandle?,
    depthMaterialBinding: MaterialBinding?,
    depthJointPaletteBinding: MaterialBinding?,
): Boolean = this.depthRenderKey == depthRenderKey && this.alphaCutoff == alphaCutoff && this.depthPipeline == depthPipeline &&
    this.depthMaterialBinding == depthMaterialBinding && this.depthJointPaletteBinding == depthJointPaletteBinding

private fun GpuResolvedDraw.holds(draw: PreparedDraw): Boolean = holdsBuffers(draw) && holdsBindings(draw) && holdsOrder(draw)

private fun GpuResolvedDraw.holdsBuffers(draw: PreparedDraw): Boolean =
    pipeline == draw.pipeline && vertexFormat == draw.vertexFormat && vertexBuffer == draw.vertexBuffer &&
        indexBuffer == draw.indexBuffer && elementCount == draw.elementCount && instances == draw.instances &&
        instanceVertexBuffer == draw.instanceVertexBuffer && instanceColorBuffer == draw.instanceColorBuffer &&
        instanceFrameBuffer == draw.instanceFrameBuffer

private fun GpuResolvedDraw.holdsBindings(draw: PreparedDraw): Boolean =
    materialBinding == draw.materialBinding && jointPaletteBinding == draw.jointPaletteBinding &&
        shadowBinding == draw.shadowBinding && sceneDepthBinding == draw.sceneDepthBinding

private fun GpuResolvedDraw.holdsOrder(draw: PreparedDraw): Boolean =
    transparent == draw.transparent && depthSortKey == draw.depthSortKey && sortOrder == draw.sortOrder &&
        batchKey == draw.batchKey && sortLayer == draw.sortLayer

/**
 * [this] draw's distance to [cameraEye], read only to order transparent draws: an opaque draw's is 0,
 * so a moving camera leaves its packet unchanged and [ResolvedDrawCache] hands it back (#1742), as
 * WebGPU's opaque draws do (#1555).
 */
internal fun GpuDrawRequest.sortKeyFor(transparent: Boolean, cameraEye: Vec3f): Float =
    if (transparent) depthSortKey(cameraEye) else 0f
