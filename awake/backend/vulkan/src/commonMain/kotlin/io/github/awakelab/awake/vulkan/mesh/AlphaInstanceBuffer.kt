/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.mesh

import io.github.awakelab.awake.core.geometry.GpuDataShape
import io.github.awakelab.awake.core.math.Vec4
import io.github.awakelab.awake.render.passes.InstancePacker
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import io.github.awakelab.awake.vulkan.gen.VulkanBuffers
import io.github.awakelab.awake.vulkan.handles.BufferHandle
import io.github.awakelab.awake.vulkan.handles.DeviceMemoryHandle
import io.github.awakelab.awake.vulkan.models.info.VkBufferCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkBufferUsageFlagBits
import io.github.awakelab.awake.vulkan.models.info.VkMemoryAllocateInfo
import io.github.awakelab.awake.vulkan.pipeline.VulkanBufferBinding

/**
 * The per-instance RGBA color+alpha behind one particle-instanced draw call -- an instance-rate
 * vertex buffer bound at binding 2, alongside the mesh's own per-vertex buffer at binding 0
 * and [InstanceBuffer]'s model matrices at binding 1 (see `RenderPipeline`'s `instanceAlpha`
 * parameter, which declares the matching vertex input state). Same
 * HOST_VISIBLE|HOST_COHERENT, fixed-capacity, rewritten-every-frame lifecycle as
 * [InstanceBuffer] itself -- this is that same shape with `FLOATS_PER_INSTANCE = 4` (one
 * `vec4f` per instance, not a generalization of [InstanceBuffer]'s own `mat4` stride) and its
 * own binding, since widening [InstanceBuffer]'s own stride would ripple into every OTHER
 * instanced format that doesn't use per-instance color at all.
 */
class AlphaInstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    /** Hard ceiling on instances per draw call -- see [InstanceBuffer]'s own doc comment for
     * why this fails loudly rather than silently truncating. */
    private val maxInstances: Int = InstanceBuffer.DEFAULT_MAX_INSTANCES,
    framesInFlight: Int = 1,
) {
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    private data class FrameResources(
        val buffer: BufferHandle,
        val memory: DeviceMemoryHandle,
    ) {
        /** This slot's buffer as the port's opaque handle -- built once, not per draw. */
        val binding = VulkanBufferBinding(buffer.handle)
    }

    private val frameResources: Array<FrameResources> = Array(framesInFlight) {
        val (buffer, memory) = allocateHostVisibleBuffer((maxInstances * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toLong())
        FrameResources(BufferHandle(buffer), DeviceMemoryHandle(memory))
    }

    // Reused across frames so a steady instance count allocates nothing per frame -- same
    // "resize only when the count actually changes" shape InstanceBuffer's own `packed` uses.

    private val packer = InstancePacker<Vec4>(GpuDataShape.Vec4, "AlphaInstanceBuffer") { out, offset, color ->
        out[offset] = color.x
        out[offset + 1] = color.y
        out[offset + 2] = color.z
        out[offset + 3] = color.w
    }

    fun update(frameIndex: Int, colors: List<Vec4>) {
        val floats = packer.pack(colors, maxInstances) ?: return
        VulkanBuffers.writeBufferMemoryFloats(device, resourcesFor(frameIndex).memory.handle, 0, floats)
    }

    /** This frame slot's buffer, for the shared opaque feature to bind at binding 2. */
    fun binding(frameIndex: Int): VulkanBufferBinding = resourcesFor(frameIndex).binding

    fun bind(frameIndex: Int, commandBuffer: Long) {
        VulkanBuffers.vkCmdBindVertexBuffers(
            commandBuffer,
            INSTANCE_ALPHA_BINDING,
            longArrayOf(resourcesFor(frameIndex).buffer.handle),
            longArrayOf(0L),
        )
    }

    fun destroy() {
        frameResources.forEach { frame ->
            VulkanBuffers.vkDestroyBuffer(device, frame.buffer.handle)
            VulkanBuffers.vkFreeMemory(device, frame.memory.handle)
        }
    }

    private fun resourcesFor(frameIndex: Int): FrameResources {
        require(frameIndex in frameResources.indices) {
            "AlphaInstanceBuffer frame index $frameIndex is outside 0..${frameResources.lastIndex}."
        }
        return frameResources[frameIndex]
    }

    private fun allocateHostVisibleBuffer(byteSize: Long): Pair<Long, Long> {
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = byteSize,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            ),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        val memory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
        )
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
        return buffer to memory
    }

    companion object {
        const val INSTANCE_ALPHA_BINDING = 2
        val FLOATS_PER_INSTANCE = GpuDataShape.Vec4.componentCount
    }
}
