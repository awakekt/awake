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
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.GPURenderPipeline
import com.awakekt.awake.render.command.BufferHandle as RenderBufferHandle

/**
 * This backend's half of the [CommandRecorder] port: one `GPURenderPassEncoder` call per method,
 * no draw-order logic (that lives once, in `SharedOpaqueRenderFeature`). The only file in this
 * module allowed to turn a shared render-feature call into a real WebGPU call.
 *
 * Constructed per pass rather than reused like Vulkan's: a `GPURenderPassEncoder` only exists
 * inside its own `beginRenderPass { }` block, so there is nothing to retarget between passes.
 */
internal class WebGpuCommandRecorder(
    private val encoder: GPURenderPassEncoder,
    /** Where every draw recorded here is counted; the owning `Renderer`'s. */
    private val stats: RenderStatsCounter?,
) : CommandRecorder {

    private var bindingLayout: BindingLayout = BindingLayout.Standard
    private var currentPipeline: WebGpuPipelineHandle? = null

    override fun bindPipeline(pipeline: PipelineHandle) {
        val webGpuPipeline = pipeline as WebGpuPipelineHandle
        encoder.setPipeline(webGpuPipeline.pipeline)
        bindingLayout = webGpuPipeline.bindingLayout
        currentPipeline = webGpuPipeline
    }

    /** WebGPU validates a bind group against the currently bound pipeline's own layout, so the
     * Vulkan side's pipeline-layout tracking has no counterpart here. */
    override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) {
        val group = bindingLayout.slot(semantic)
        if (currentPipeline?.hasBindingGroup(group) != true) return
        encoder.setBindGroup(group.toUInt(), (binding as WebGpuBindGroupHandle).bindGroup)
    }

    override fun bindVertexBuffer(binding: Int, buffer: RenderBufferHandle) {
        encoder.setVertexBuffer(binding.toUInt(), (buffer as WebGpuBufferHandle).buffer)
    }

    override fun bindIndexBuffer(buffer: RenderBufferHandle) {
        encoder.setIndexBuffer((buffer as WebGpuBufferHandle).buffer, meshIndexFormat)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        encoder.setScissorRect(x.toUInt(), y.toUInt(), width.toUInt(), height.toUInt())
    }

    override fun draw(vertexCount: Int, instanceCount: Int) {
        encoder.draw(vertexCount.toUInt(), instanceCount.toUInt())
        stats?.recordDraw(vertexCount, instanceCount, currentPipeline?.drawsTriangles ?: true)
    }

    override fun drawIndexed(indexCount: Int, instanceCount: Int) {
        encoder.drawIndexed(indexCount.toUInt(), instanceCount.toUInt())
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
) : PipelineHandle

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
