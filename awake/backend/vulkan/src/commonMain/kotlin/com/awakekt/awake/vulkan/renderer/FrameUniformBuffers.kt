/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.passes.uniforms.FrameUniformBlock
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.identityFrameBlock
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/**
 * The device's frame blocks, one per frame in flight: what the camera, the light and the shadow
 * cascades decide for every lit draw of a batch. Each material descriptor set of a frame binds
 * that frame's buffer beside the draw's own block, at
 * [com.awakekt.awake.render.pipeline.FRAME_UNIFORM_BINDING].
 *
 * Written once per batch, where a draw's block used to carry all of it. A buffer per frame in
 * flight for the reason a draw's slots have one: the GPU may still be reading last frame's.
 *
 * Two per frame, because a batch's wireframe-overlay edges are a pass of their own within it:
 * they draw through a view-projection pulled toward the eye and in the edge debug code, so an
 * edge draw's set binds the second.
 *
 * And a third that never changes, [identityFrameBlock]: what the set of a draw whose single block
 * starts with its whole MVP binds, so the shared camera-depth shader draws it as before.
 */
internal class FrameUniformBuffers(private val graphicsDevice: GraphicsDevice) {
    private class Entry(val buffer: Long, val memory: Long)

    private val entries = ArrayList<Entry>()
    private val block = FrameUniformBlock()

    /** The buffer of [kind] that frame [frameIndex]'s descriptor sets bind, made on first use. */
    fun bufferFor(frameIndex: Int, kind: FrameBlockKind = FrameBlockKind.Scene): Long = entry(frameIndex, kind).buffer

    /** Writes [context]'s frame block into frame [frameIndex]'s buffer, the batch's or its [edges]'. */
    fun write(frameIndex: Int, context: GpuDrawPreparationContext, cascades: GpuShadowCascadeData?, edges: Boolean = false) {
        val kind = if (edges) FrameBlockKind.Edges else FrameBlockKind.Scene
        val environment = context.environment
        val floats = block.pack(
            viewProjection = context.viewProjection,
            cameraEye = context.cameraEye,
            cameraForward = context.cameraForward,
            lightPayload = context.passUniforms,
            shadowCascades = cascades,
            fogColor = environment.fogColor,
            fogDensity = environment.fogDensity,
            debugView = environment.debugView,
            exposure = environment.exposure,
        )
        VulkanBuffers.writeBufferMemoryFloats(graphicsDevice.device, entry(frameIndex, kind).memory, 0L, floats)
    }

    /** A frame's buffers sit side by side, one per [FrameBlockKind]. */
    private fun entry(frameIndex: Int, kind: FrameBlockKind): Entry {
        val index = frameIndex * KINDS.size + kind.ordinal
        while (entries.size <= index) {
            val created = create()
            // The identity block is written here, once, and never again.
            if (KINDS[entries.size % KINDS.size] == FrameBlockKind.Identity) {
                VulkanBuffers.writeBufferMemoryFloats(graphicsDevice.device, created.memory, 0L, identityFrameBlock())
            }
            entries += created
        }
        return entries[index]
    }

    private fun create(): Entry {
        val device = graphicsDevice.device
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(size = BYTES, usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(
                allocationSize = requirements.size,
                memoryTypeIndex = VulkanBuffers.findMemoryType(
                    graphicsDevice.physicalDevice,
                    requirements.memoryTypeBits,
                    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                        VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                ),
            ),
        )
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0L)
        return Entry(buffer, memory)
    }

    /** Frees every buffer; the device must be idle. */
    fun destroy() {
        val device = graphicsDevice.device
        entries.forEach {
            VulkanBuffers.vkDestroyBuffer(device, it.buffer)
            VulkanBuffers.vkFreeMemory(device, it.memory)
        }
        entries.clear()
    }

    companion object {
        /** The frame block's size, which a descriptor's range and the buffer share. */
        val BYTES: Long = (MaterialUniformLayouts.Frame.total * Float.SIZE_BYTES).toLong()

        private val KINDS = FrameBlockKind.entries
    }
}

/** Which frame block a draw's descriptor set binds beside the draw's own block. */
internal enum class FrameBlockKind {
    /** The batch's: its camera, light and cascades. */
    Scene,

    /** The batch's wireframe-overlay edges': the pulled view-projection and the edge debug code. */
    Edges,

    /** The identity view-projection, for a draw whose single block starts with its whole MVP. */
    Identity,
}
