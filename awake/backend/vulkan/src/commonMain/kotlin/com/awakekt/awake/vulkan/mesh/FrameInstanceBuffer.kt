/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.render.passes.InstanceWriter
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.allocateHostVisibleBufferMemory
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.handles.BufferHandle
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding

/**
 * The per-instance sprite-strip frame index behind one particle-instanced draw call -- an
 * instance-rate vertex buffer bound at binding 3, alongside the mesh's own per-vertex buffer at
 * binding 0, [InstanceBuffer]'s model matrices at binding 1, and [AlphaInstanceBuffer]'s
 * color+alpha at binding 2 (see `RenderPipeline`'s `instanceFrame` parameter, which declares the
 * matching vertex input state). Same HOST_VISIBLE|HOST_COHERENT, fixed-capacity,
 * rewritten-every-frame lifecycle as [AlphaInstanceBuffer] itself, just `FLOATS_PER_INSTANCE = 1`
 * (a lone `f32`, not a `vec4f`) and its own binding.
 */
class FrameInstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    private val maxInstances: Int = InstanceBuffer.DEFAULT_MAX_INSTANCES,
    framesInFlight: Int = 1,
) {
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    private data class FrameResources(
        val buffer: BufferHandle,
        val memory: DeviceMemoryHandle,
        val mappedPtr: Long,
    ) {
        /** This slot's buffer as the port's opaque handle -- built once, not per draw. */
        val binding = VulkanBufferBinding(buffer.handle)
    }

    private val frameResources: Array<FrameResources> = Array(framesInFlight) {
        val (buffer, memory, mappedPtr) = allocateHostVisibleBuffer((maxInstances * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toLong())
        FrameResources(BufferHandle(buffer), DeviceMemoryHandle(memory), mappedPtr)
    }

    private val packer = InstancePacker<Float>(
        GpuDataShape.Float,
        "FrameInstanceBuffer",
        write = InstanceWriter { out, offset, frame ->
            out[offset] = frame
        },
    )

    /**
     * Writes one sprite-strip frame index per instance into [frameIndex]'s buffer, in the same
     * order as the instance matrices. An empty list writes nothing and leaves the previous contents
     * in place.
     *
     * @param frameIndex The frame slot to write.
     * @param frames Per-instance frame indices into the sprite strip, one entry per drawn instance.
     * @throws IllegalArgumentException If [frames] holds more entries than the instance ceiling, or
     * [frameIndex] is not a valid slot.
     */
    fun update(frameIndex: Int, frames: List<Float>) {
        val count = packer.packSlice(frames, maxInstances)
        if (count == 0) return
        VulkanBuffers.writeMappedMemoryFloats(resourcesFor(frameIndex).mappedPtr, 0L, packer.buffer, 0, count)
    }

    /** This frame slot's buffer, for the shared opaque feature to bind at binding 3. */
    fun binding(frameIndex: Int): VulkanBufferBinding = resourcesFor(frameIndex).binding

    /**
     * Binds [frameIndex]'s buffer as the instance-rate vertex buffer at binding 3.
     *
     * @param frameIndex The frame slot whose buffer to bind.
     * @param commandBuffer The command buffer being recorded.
     * @throws IllegalArgumentException If [frameIndex] is not a valid slot.
     */
    fun bind(frameIndex: Int, commandBuffer: Long) {
        VulkanBuffers.vkCmdBindVertexBuffer(
            commandBuffer,
            INSTANCE_FRAME_BINDING,
            resourcesFor(frameIndex).buffer.handle,
            0L,
        )
    }

    /**
     * Destroys and frees every frame slot's buffer and memory. Call once, after the GPU has
     * finished with them.
     */
    fun destroy() {
        frameResources.forEach { frame ->
            VulkanBuffers.vkDestroyBuffer(device, frame.buffer.handle)
            if (frame.mappedPtr != 0L) VulkanBuffers.vkUnmapMemory(device, frame.memory.handle)
            VulkanBuffers.vkFreeMemory(device, frame.memory.handle)
        }
    }

    private fun resourcesFor(frameIndex: Int): FrameResources {
        require(frameIndex in frameResources.indices) {
            "FrameInstanceBuffer frame index $frameIndex is outside 0..${frameResources.lastIndex}."
        }
        return frameResources[frameIndex]
    }

    private fun allocateHostVisibleBuffer(byteSize: Long): Triple<Long, Long, Long> {
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = byteSize,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
            ),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memory = graphicsDevice.allocateHostVisibleBufferMemory(requirements)
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
        val mappedPtr = VulkanBuffers.vkMapMemory(device, memory, 0L, requirements.size)
        return Triple(buffer, memory, mappedPtr)
    }

    /** Binding index and stride constants for the per-instance frame-index stream. */
    companion object {
        /**
         * Vertex-buffer binding index of the per-instance frame stream: 3, after the mesh at 0, the
         * model matrices at 1 and the colours at 2.
         */
        const val INSTANCE_FRAME_BINDING = 3

        /** Floats per instance: 1, a lone `f32` frame index. */
        val FLOATS_PER_INSTANCE = GpuDataShape.Float.componentCount
    }
}
