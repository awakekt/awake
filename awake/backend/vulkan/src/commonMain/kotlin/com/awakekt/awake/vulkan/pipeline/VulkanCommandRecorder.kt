/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.models.info.VkIndexType
import com.awakekt.awake.render.command.BufferHandle as RenderBufferHandle

/**
 * This backend's half of the [CommandRecorder] port: one `vkCmd*` call per method, no draw-order
 * logic (that lives once, in `SharedOpaqueRenderFeature`). The only file in this module allowed to
 * turn a shared render-feature call into a real Vulkan call.
 *
 * [commandBuffer] is a `var` rather than a constructor parameter because one frame records into
 * several buffers (the swapchain frame's, and the offscreen one `renderToTexture` reuses) -- one
 * recorder per `Renderer`, retargeted, beats allocating one per record site per frame. Safe for
 * the same reason `Renderer`'s other per-frame scratch state is: a `Renderer` records from one
 * thread.
 */
internal class VulkanCommandRecorder : CommandRecorder {
    var commandBuffer: Long = 0

    /**
     * Descriptor sets the engine owns and rebinds per frame, by what they mean.
     *
     * Shadow depth and scene depth are both this: written by a pre-pass, read by whichever
     * pipelines declare them, and not owned by any draw. A map rather than a field each, so a
     * third engine-provided group needs no new plumbing here -- and so the two cannot silently
     * contend for one slot the way a single handle did.
     */
    var engineDescriptorSets: Map<BindingSemantic, Long> = emptyMap()

    /** Set by [bindPipeline]. Vulkan needs the bound pipeline's layout to bind a descriptor set;
     * WebGPU's `setBindGroup` doesn't, which is why [CommandRecorder.bindMaterial] doesn't carry
     * one and this is tracked here instead. */
    private var boundPipelineLayout: Long = 0
    private var bindingLayout: BindingLayout = BindingLayout.Standard

    override fun bindPipeline(pipeline: PipelineHandle) {
        val vulkanPipeline = pipeline as VulkanPipelineHandle
        Vulkan.vkCmdBindPipeline(
            commandBuffer,
            VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
            vulkanPipeline.pipelineHandle,
        )
        boundPipelineLayout = vulkanPipeline.pipelineLayoutHandle
        bindingLayout = vulkanPipeline.bindingLayout
        engineDescriptorSets.forEach { (semantic, handle) ->
            if (handle != 0L && semantic in vulkanPipeline.engineBoundSemantics) {
                VulkanDescriptors.vkCmdBindDescriptorSet(
                    commandBuffer,
                    boundPipelineLayout,
                    bindingLayout.slot(semantic),
                    handle,
                )
            }
        }
    }

    override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) {
        VulkanDescriptors.vkCmdBindDescriptorSet(
            commandBuffer,
            boundPipelineLayout,
            bindingLayout.slot(semantic),
            (binding as VulkanMaterialBinding).descriptorSetHandle,
        )
    }

    override fun bindVertexBuffer(binding: Int, buffer: RenderBufferHandle) {
        VulkanBuffers.vkCmdBindVertexBuffers(
            commandBuffer,
            binding,
            (buffer as VulkanBufferBinding).asArray,
            ZERO_OFFSET,
        )
    }

    override fun bindIndexBuffer(buffer: RenderBufferHandle) {
        VulkanBuffers.vkCmdBindIndexBuffer(
            commandBuffer,
            (buffer as VulkanBufferBinding).handle,
            0,
            VkIndexType.VK_INDEX_TYPE_UINT32,
        )
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        Vulkan.vkCmdSetScissor(
            commandBuffer,
            0,
            arrayOf(
                com.awakekt.awake.vulkan.models.VkRect2D(
                    offset = com.awakekt.awake.vulkan.models.VkOffset2D(x = x, y = y),
                    extent = com.awakekt.awake.vulkan.models.VkExtent2D(width = width, height = height),
                ),
            ),
        )
    }

    override fun draw(vertexCount: Int, instanceCount: Int) {
        Vulkan.vkCmdDraw(commandBuffer, vertexCount, instanceCount, 0, 0)
    }

    override fun drawIndexed(indexCount: Int, instanceCount: Int) {
        VulkanBuffers.vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, 0, 0, 0)
    }

    private companion object {
        /** Every vertex buffer in this backend is bound at offset 0; shared so the bind path
         * allocates nothing. */
        val ZERO_OFFSET = longArrayOf(0L)
    }
}

/** A pipeline the shared layer can bind. Implemented by every real pipeline class here, which is
 * why the recorder above can cast unconditionally. */
interface VulkanPipelineHandle : PipelineHandle {
    val pipelineHandle: Long
    val pipelineLayoutHandle: Long

    /**
     * Which engine-owned groups this pipeline's layout actually declares.
     *
     * Binding one a pipeline did not declare a set layout for is a validation error, so the
     * recorder binds only what is named here. Empty for a pipeline that reads none.
     */
    val engineBoundSemantics: Set<BindingSemantic>
}

/** A descriptor set the shared layer can bind at some set index. Implemented by the already-
 * allocated per-frame/per-draw slot objects (`Material`'s uniform slots, `PerFrameUniformSlots
 * .Slot`, `SkinnedInstanceBuffer`'s frame resources), so resolving one per draw allocates
 * nothing. */
interface VulkanMaterialBinding : MaterialBinding {
    val descriptorSetHandle: Long
}

/**
 * A `VkBuffer` the shared layer can bind. Built once per buffer at resource-creation time, not
 * per draw -- [asArray] is the single-element array `vkCmdBindVertexBuffers` takes, kept here so
 * the per-frame bind path allocates nothing (the hand-written `Mesh.bind` it replaces built a
 * fresh one every call).
 */
class VulkanBufferBinding(val handle: Long) : RenderBufferHandle {
    val asArray = longArrayOf(handle)
}
