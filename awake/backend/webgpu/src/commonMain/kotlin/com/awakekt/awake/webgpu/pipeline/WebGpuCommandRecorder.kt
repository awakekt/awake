/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.webgpu.mesh.meshIndexFormat
import com.awakekt.awake.webgpu.pipeline.bundles.RetainedBundle
import com.awakekt.awake.webgpu.pipeline.bundles.RetainedCells
import com.awakekt.awake.webgpu.pipeline.bundles.RetainedPassBundles
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.GPURenderPipeline
import com.awakekt.awake.render.command.BufferHandle as RenderBufferHandle

/**
 * This backend's half of the [CommandRecorder] port: one `GPURenderPassEncoder` call per method,
 * unless [PassBindings] finds the binding already in place, and no draw-order logic (that lives once, in `SharedOpaqueRenderFeature`). The only file in this
 * module allowed to turn a shared render-feature call into a real WebGPU call.
 *
 * Constructed per pass rather than reused like Vulkan's: a `GPURenderPassEncoder` only exists
 * inside its own `beginRenderPass { }` block, so there is nothing to retarget between passes.
 */
internal class WebGpuCommandRecorder(
    private val encoder: GPURenderPassEncoder,
    /** Where every draw recorded here is counted; the owning `Renderer`'s. */
    private val stats: RenderStatsCounter?,
    /** This pass's retained bundles, or null for a pass that records every draw directly. */
    private val retained: RetainedPassBundles? = null,
) : CommandRecorder {

    private var bindingLayout: BindingLayout = BindingLayout.Standard
    private var currentPipeline: WebGpuPipelineHandle? = null

    private val bound = PassBindings()

    /** The open retained region: binds and draws are captured into its cells, not sent. */
    private var region: RetainedCells<RetainedBundle>? = null

    /** Captures what follows into [key]'s cells, on a pass that keeps bundles; elsewhere a no-op. */
    override fun beginRetained(key: Any) {
        region = retained?.region(key)?.also { it.begin() }
    }

    /** Executes the region's bundles in one call. A bundle leaves nothing bound, so the pass forgets its binds. */
    override fun endRetained() {
        val open = region ?: return
        region = null
        val bundles = checkNotNull(retained).settle(open)
        if (bundles.isNotEmpty()) encoder.executeBundles(bundles)
        bound.forget()
        currentPipeline = null
    }

    override fun bindPipeline(pipeline: PipelineHandle) {
        val webGpuPipeline = pipeline as WebGpuPipelineHandle
        bindingLayout = webGpuPipeline.bindingLayout
        currentPipeline = webGpuPipeline
        region?.let { return it.bindPipeline(webGpuPipeline.pipeline) }
        encoder.setPipeline(webGpuPipeline.pipeline)
        bound.forget()
    }

    /** WebGPU validates a bind group against the currently bound pipeline's own layout, so the
     * Vulkan side's pipeline-layout tracking has no counterpart here. */
    override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) {
        val group = bindingLayout.slot(semantic)
        if (currentPipeline?.hasBindingGroup(group) != true) return
        val bindGroup = (binding as WebGpuBindGroupHandle).bindGroup
        region?.let { return it.bindGroup(group, bindGroup) }
        if (bound.bindGroup(group, bindGroup)) encoder.bindGroup(group.toUInt(), bindGroup)
    }

    override fun bindVertexBuffer(binding: Int, buffer: RenderBufferHandle) {
        val gpuBuffer = (buffer as WebGpuBufferHandle).buffer
        region?.let { return it.bindVertexBuffer(binding, gpuBuffer) }
        if (bound.bindVertexBuffer(binding, gpuBuffer)) encoder.setVertexBuffer(binding.toUInt(), gpuBuffer)
    }

    /** Every index buffer here is bound whole in [meshIndexFormat], so the buffer alone says whether it is bound. */
    override fun bindIndexBuffer(buffer: RenderBufferHandle) {
        val gpuBuffer = (buffer as WebGpuBufferHandle).buffer
        region?.let { return it.bindIndexBuffer(gpuBuffer) }
        if (bound.bindIndexBuffer(gpuBuffer)) encoder.setIndexBuffer(gpuBuffer, meshIndexFormat)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        encoder.setScissorRect(x.toUInt(), y.toUInt(), width.toUInt(), height.toUInt())
    }

    override fun draw(vertexCount: Int, instanceCount: Int) {
        val open = region
        if (open != null) open.draw(indexed = false, vertexCount, instanceCount) else encoder.draw(vertexCount.toUInt(), instanceCount.toUInt())
        stats?.recordDraw(vertexCount, instanceCount, currentPipeline?.drawsTriangles ?: true)
    }

    override fun drawIndexed(indexCount: Int, instanceCount: Int) {
        val open = region
        if (open != null) open.draw(indexed = true, indexCount, instanceCount) else encoder.drawIndexed(indexCount.toUInt(), instanceCount.toUInt())
        stats?.recordDraw(indexCount, instanceCount, currentPipeline?.drawsTriangles ?: true)
    }
}

/** Each wraps the WebGPU object the shared layer passes through without inspecting. Built once
 * with the resource it points at (a pipeline, a bind group, a buffer), not per draw. */
class WebGpuPipelineHandle(
    /**
     * The wrapped render pipeline. A `var` because a shader replacement swaps the pipeline in
     * place, keeping every holder of this handle valid.
     */
    var pipeline: GPURenderPipeline,
    override val bindingLayout: BindingLayout = BindingLayout.Standard,
    /**
     * What a textured material must bind in group 0 for this pipeline, when the pipeline declares
     * it; `null` otherwise.
     */
    val materialBindings: GroupBindings? = null,
    /**
     * Whether the pipeline's shader declares a group 0 at all. Consulted only when
     * [bindingsByGroup] is empty (a legacy pipeline).
     */
    val hasGroupZeroBindings: Boolean = true,
    /**
     * Exact shader resource layout keyed by bind-group index, when the pipeline carries it; empty
     * for legacy pipelines without metadata.
     */
    val bindingsByGroup: Map<Int, GroupBindings> = emptyMap(),
    override val drawsTriangles: Boolean = true,
) : PipelineHandle {
    /**
     * Floats of the draw's own block when this pipeline's shader also reads the frame block, at
     * [com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING]; 0 when its one block carries
     * everything. Read per draw, so resolved once.
     */
    internal val splitDrawBlockFloats: Int =
        if (uniformByteSize(0, com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING) > 0L) {
            (uniformByteSize(0, 0) / Float.SIZE_BYTES).toInt()
        } else {
            0
        }
}

/**
 * What a textured material binds in group 0: the pipeline's declared material bindings, else its
 * shader's own group 0, else the standard material set. A bind group must match the layout exactly.
 */
val WebGpuPipelineHandle.texturedMaterialBindings: GroupBindings
    get() = materialBindings ?: bindingsByGroup[0] ?: GroupBindings.StandardMaterial

/** Legacy pipelines omit metadata; retain their historical group-0 behavior until migrated. */
fun WebGpuPipelineHandle.hasBindingGroup(group: Int): Boolean =
    if (bindingsByGroup.isEmpty()) group == 0 && hasGroupZeroBindings else group in bindingsByGroup

/** Minimum uniform buffer byte size declared by this pipeline for [group] and [binding], or 0 if undeclared. */
fun WebGpuPipelineHandle.uniformByteSize(group: Int = 0, binding: Int = 0): Long {
    val fromGroup = bindingsByGroup[group]?.uniformBufferSize(binding) ?: 0L
    if (fromGroup > 0L) return fromGroup
    if (group == 0) {
        val fromMaterial = materialBindings?.uniformBufferSize(binding) ?: 0L
        if (fromMaterial > 0L) return fromMaterial
    }
    return 0L
}

/**
 * A `GPUBindGroup` as the shared render layer's opaque [MaterialBinding].
 *
 * @property bindGroup The wrapped bind group, set on the render pass encoder when this binding is
 * recorded. Bind groups are immutable and tied to the pipeline layout they were built against.
 */
class WebGpuBindGroupHandle(val bindGroup: GPUBindGroup) : MaterialBinding

/**
 * A `GPUBuffer` as the shared render layer's opaque [RenderBufferHandle], used for both vertex and
 * index buffers.
 *
 * @property buffer The wrapped buffer, bound on the render pass encoder when this handle is
 * recorded. The handle does not own it: whoever created the buffer closes it.
 */
class WebGpuBufferHandle(val buffer: GPUBuffer) : RenderBufferHandle
