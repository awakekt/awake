/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Mat4
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
import com.awakekt.awake.vulkan.renderer.sameBitsAs

/**
 * The per-instance model matrices behind one instanced draw call -- an instance-rate vertex
 * buffer bound at binding 1, alongside the mesh's own per-vertex buffer at binding 0 (see
 * `RenderPipeline`'s `instanced` parameter, which declares the matching vertex input state).
 *
 * Same HOST_VISIBLE|HOST_COHERENT, rewritten-every-frame lifecycle as
 * [com.awakekt.awake.vulkan.ui.DynamicMesh] (whose `allocateHostVisibleBuffer` this
 * mirrors) rather than `Mesh`'s DEVICE_LOCAL one-time staging copy: the transform list changes
 * per frame, so there is nothing to upload once. Simpler than `DynamicMesh` though -- one
 * vertex buffer, no index buffer, no quad vertex layout.
 *
 * Each frame slot grows to the largest set it has held, in powers of two, rather than starting at
 * [maxInstances]: a frame of batched props has one of these per instanced draw, mostly small.
 */
class InstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    /** Hard ceiling on instances per draw call. Raise it here (or per call site) rather than
     * silently truncating -- [update] fails loudly with a message naming this knob. */
    private val maxInstances: Int = DEFAULT_MAX_INSTANCES,
    framesInFlight: Int = 1,
) {
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    private data class FrameResources(
        val buffer: BufferHandle,
        val memory: DeviceMemoryHandle,
        val mappedPtr: Long,
        val capacity: Int,
    ) {
        /** This slot's buffer as the port's opaque handle -- built once, not per draw. */
        val binding = VulkanBufferBinding(buffer.handle)

        /** What [buffer] holds, as [update] last wrote it, or null when it holds nothing yet. */
        var resident: FloatArray? = null
        var residentCount = 0
    }

    private val frameResources = arrayOfNulls<FrameResources>(framesInFlight)

    /** Outgrown buffers. A frame still in flight may read one, so they are freed in [destroy]. */
    private val retired = ArrayList<FrameResources>()

    /** Uploads [update] made, for tests: one a frame slot whose models changed, none for one whose held. */
    internal var uploads = 0L
        private set

    // Grow-only scratch buffer reused across frames without reallocating as visible instance counts fluctuate.

    /** Packs [models] into this frame slot's buffer. `Mat4.data` is already column-major, which
     * is the order `instanced.wgsl`'s 4 `vec4` attributes reassemble into a `mat4x4` -- so this
     * is a straight copy, no transpose. */
    private val packer = InstancePacker<Mat4>(
        GpuDataShape.Mat4,
        "InstanceBuffer",
        write = InstanceWriter { out, offset, model ->
            model.data.copyInto(out, offset)
        },
    )

    /**
     * Writes one model matrix per instance into [frameIndex]'s buffer, growing that slot's buffer
     * first when the instances do not fit.
     *
     * Capacity grows in powers of two up to the construction-time ceiling. A buffer that is
     * outgrown is kept until [destroy] rather than freed, because a frame still in flight may be
     * reading it. An empty list writes nothing.
     *
     * @param frameIndex The frame slot to write.
     * @param models One column-major model matrix per instance.
     * @throws IllegalArgumentException If [models] holds more entries than the ceiling, or
     * [frameIndex] is not a valid slot.
     */
    fun update(frameIndex: Int, models: List<Mat4>) {
        val count = packer.packSlice(models, maxInstances)
        if (count == 0) return
        val floats = packer.buffer
        val resources = resourcesFor(frameIndex, models.size)
        // Copies of a static prop hold still frame after frame: the slot's buffer already has them (#1452).
        val resident = resources.resident
        if (resident != null && resources.residentCount == count && resident.sameBitsAs(floats, count)) return
        VulkanBuffers.writeMappedMemoryFloats(resources.mappedPtr, 0L, floats, 0, count)
        uploads += 1
        val target = if (resident != null && resident.size >= count) resident else FloatArray(count).also { resources.resident = it }
        floats.copyInto(target, 0, 0, count)
        resources.residentCount = count
    }

    /** This frame slot's buffer, for the shared opaque feature to bind at binding 1. */
    fun binding(frameIndex: Int): VulkanBufferBinding = resourcesFor(frameIndex).binding

    /**
     * Binds [frameIndex]'s buffer as the instance-rate vertex buffer at binding 1.
     *
     * @param frameIndex The frame slot whose buffer to bind.
     * @param commandBuffer The command buffer being recorded.
     * @throws IllegalArgumentException If [frameIndex] is not a valid slot.
     */
    fun bind(frameIndex: Int, commandBuffer: Long) {
        VulkanBuffers.vkCmdBindVertexBuffer(
            commandBuffer,
            INSTANCE_BINDING,
            resourcesFor(frameIndex).buffer.handle,
            0L,
        )
    }

    /**
     * Destroys and frees every frame slot's buffer, including the outgrown ones kept alive for
     * frames in flight. Call once, after the GPU has finished with them.
     */
    fun destroy() {
        (frameResources.filterNotNull() + retired).forEach { frame ->
            VulkanBuffers.vkDestroyBuffer(device, frame.buffer.handle)
            if (frame.mappedPtr != 0L) VulkanBuffers.vkUnmapMemory(device, frame.memory.handle)
            VulkanBuffers.vkFreeMemory(device, frame.memory.handle)
        }
    }

    /** [frameIndex]'s buffer, grown first if it holds fewer than [instances]. */
    private fun resourcesFor(frameIndex: Int, instances: Int = 1): FrameResources {
        require(frameIndex in frameResources.indices) {
            "InstanceBuffer frame index $frameIndex is outside 0..${frameResources.lastIndex}."
        }
        val current = frameResources[frameIndex]
        if (current != null && current.capacity >= instances) return current
        current?.let(retired::add)
        var capacity = MIN_CAPACITY
        while (capacity < instances) capacity *= 2
        capacity = capacity.coerceAtMost(maxInstances)
        graphicsDevice.instanceBuffersCreated += 1
        val (buffer, memory, mappedPtr) = allocateHostVisibleBuffer((capacity * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toLong())
        return FrameResources(BufferHandle(buffer), DeviceMemoryHandle(memory), mappedPtr, capacity).also { frameResources[frameIndex] = it }
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

    /** Stride and capacity constants for the per-instance model-matrix stream. */
    companion object {
        /** One `mat4` per instance. */
        val FLOATS_PER_INSTANCE = GpuDataShape.Mat4.componentCount

        /** 4096 * 64 B = 256 KB per frame slot -- far above what any current demo scatters,
         * cheap enough not to need tuning, and overridable per call site when one does. */
        const val DEFAULT_MAX_INSTANCES = 4096

        private const val INSTANCE_BINDING = 1
        private const val MIN_CAPACITY = 16
    }
}
