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
internal class WebGpuCommandRecorder(private val encoder: GPURenderPassEncoder) : CommandRecorder {

    private var bindingLayout: BindingLayout = BindingLayout.Standard

    override fun bindPipeline(pipeline: PipelineHandle) {
        val webGpuPipeline = pipeline as WebGpuPipelineHandle
        encoder.setPipeline(webGpuPipeline.pipeline)
        bindingLayout = webGpuPipeline.bindingLayout
    }

    /** WebGPU validates a bind group against the currently bound pipeline's own layout, so the
     * Vulkan side's pipeline-layout tracking has no counterpart here. */
    override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) {
        encoder.setBindGroup(bindingLayout.slot(semantic).toUInt(), (binding as WebGpuBindGroupHandle).bindGroup)
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
    }

    override fun drawIndexed(indexCount: Int, instanceCount: Int) {
        encoder.drawIndexed(indexCount.toUInt(), instanceCount.toUInt())
    }
}

/** Each wraps the WebGPU object the shared layer passes through without inspecting. Built once
 * with the resource it points at (a pipeline, a bind group, a buffer), not per draw. */
class WebGpuPipelineHandle(
    val pipeline: GPURenderPipeline,
    override val bindingLayout: BindingLayout = BindingLayout.Standard,
) : PipelineHandle

class WebGpuBindGroupHandle(val bindGroup: GPUBindGroup) : MaterialBinding

class WebGpuBufferHandle(val buffer: GPUBuffer) : RenderBufferHandle
