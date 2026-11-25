/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.passes.InstancePacker
import com.awakekt.awake.render.passes.InstanceWriter
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.allocateHostVisibleBufferMemory
import com.awakekt.awake.vulkan.device.destroyOrRetire
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.handles.BufferHandle
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding
import com.awakekt.awake.vulkan.renderer.sameBitsAs

/**
 * The per-instance RGBA color+alpha behind one particle-instanced or tinted-instanced draw call --
 * an instance-rate vertex buffer bound at binding 2, alongside the mesh's own per-vertex buffer at
 * binding 0 and [InstanceBuffer]'s model matrices at binding 1 (see `RenderPipeline`'s
 * `instanceAlpha` parameter, which declares the matching vertex input state).
 *
 * Same HOST_VISIBLE|HOST_COHERENT, grow-on-demand lifecycle as [InstanceBuffer], with
 * `FLOATS_PER_INSTANCE = 4` (one `vec4f` per instance). Each frame slot starts unallocated and
 * grows in powers of two from [MIN_CAPACITY] up to [maxInstances], retiring outgrown buffers
 * through the device reaper once frames in flight complete.
 */
class AlphaInstanceBuffer(
    private val graphicsDevice: GraphicsDevice,
    /** Hard ceiling on instances per draw call -- see [InstanceBuffer]'s own doc comment for
     * why this fails loudly rather than silently truncating. */
    private val maxInstances: Int = InstanceBuffer.DEFAULT_MAX_INSTANCES,
    framesInFlight: Int = 1,
) {
    private val device get() = graphicsDevice.device

    private data class FrameResources(
        val buffer: BufferHandle,
        val memory: DeviceMemoryHandle,
        val mappedPtr: Long,
        val capacity: Int,
        val byteSize: Long,
    ) {
        /** This slot's buffer as the port's opaque handle -- built once, not per draw. */
        val binding = VulkanBufferBinding(buffer.handle)
        var holdsWhite: Boolean = false
        var resident: FloatArray? = null
        var residentCount: Int = 0

        fun matchesResident(floats: FloatArray, count: Int): Boolean =
            !holdsWhite && residentCount == count && resident?.sameBitsAs(floats, count) == true
    }

    private val frameResources = arrayOfNulls<FrameResources>(framesInFlight)

    /** Uploads [update] or [fillWhite] made, for tests. */
    internal var uploads = 0L
        private set

    /** Outgrown slot buffers handed to the deferred reaper so far, for tests. */
    internal var retiredCount = 0L
        private set

    /** Current allocated instance capacity for [frameIndex], or 0 before first use. */
    internal fun capacityFor(frameIndex: Int): Int = frameResources.getOrNull(frameIndex)?.capacity ?: 0

    /** Total requested GPU buffer bytes currently held across live frame slots, for tests. */
    internal val allocatedBytes: Long
        get() {
            var total = 0L
            for (i in frameResources.indices) {
                total += frameResources[i]?.byteSize ?: 0L
            }
            return total
        }

    // Reused across frames so a steady instance count allocates nothing per frame -- same
    // "resize only when the count actually changes" shape InstanceBuffer's own `packed` uses.
    private val packer = InstancePacker<Vec4>(
        GpuDataShape.Vec4,
        "AlphaInstanceBuffer",
        write = InstanceWriter { out, offset, color ->
            out[offset] = color.x
            out[offset + 1] = color.y
            out[offset + 2] = color.z
            out[offset + 3] = color.w
        },
    )

    /**
     * Writes one RGBA tint per instance into [frameIndex]'s buffer, in the same order as the
     * instance matrices, growing that slot's buffer on demand. An empty list writes nothing and
     * leaves the previous contents in place.
     *
     * @param frameIndex The frame slot to write.
     * @param colors Per-instance colour and alpha, one entry per drawn instance.
     * @throws IllegalArgumentException If [colors] holds more entries than the instance ceiling, or
     * [frameIndex] is not a valid slot.
     */
    fun update(frameIndex: Int, colors: List<Vec4>) {
        val count = packer.packSlice(colors, maxInstances)
        if (count == 0) return
        val floats = packer.buffer
        val resources = resourcesFor(frameIndex, colors.size)
        if (resources.matchesResident(floats, count)) return
        val resident = resources.resident
        VulkanBuffers.writeMappedMemoryFloats(resources.mappedPtr, 0L, floats, 0, count)
        uploads += 1L
        val target = if (resident != null && resident.size >= count) resident else FloatArray(count).also { resources.resident = it }
        floats.copyInto(target, 0, 0, count)
        resources.residentCount = count
        resources.holdsWhite = false
    }

    /**
     * Every instance (1, 1, 1, 1) in [frameIndex]'s buffer up to [maxInstances]: untinted and
     * unfaded, for an instanced draw on a tinted pipeline that carries no colours. A slot already
     * holding it is left alone.
     */
    fun fillWhite(frameIndex: Int) {
        fillWhite(frameIndex, maxInstances)
    }

    internal fun fillWhite(frameIndex: Int, instanceCount: Int) {
        require(instanceCount in 1..maxInstances) {
            "AlphaInstanceBuffer fillWhite count $instanceCount is outside 1..$maxInstances."
        }
        val resources = resourcesFor(frameIndex, instanceCount)
        if (resources.holdsWhite) return
        val white = FloatArray(resources.capacity * FLOATS_PER_INSTANCE) { 1f }
        VulkanBuffers.writeMappedMemoryFloats(resources.mappedPtr, 0L, white)
        uploads += 1L
        resources.holdsWhite = true
        resources.resident = null
        resources.residentCount = 0
    }

    /** This frame slot's buffer, for the shared opaque feature to bind at binding 2. */
    fun binding(frameIndex: Int): VulkanBufferBinding = resourcesFor(frameIndex).binding

    /**
     * Binds [frameIndex]'s buffer as the instance-rate vertex buffer at binding 2.
     *
     * @param frameIndex The frame slot whose buffer to bind.
     * @param commandBuffer The command buffer being recorded.
     * @throws IllegalArgumentException If [frameIndex] is not a valid slot.
     */
    fun bind(frameIndex: Int, commandBuffer: Long) {
        VulkanBuffers.vkCmdBindVertexBuffer(
            commandBuffer,
            INSTANCE_ALPHA_BINDING,
            resourcesFor(frameIndex).buffer.handle,
            0L,
        )
    }

    /**
     * Destroys and frees every live frame slot's buffer and memory. Call once, after the GPU has
     * finished with them.
     */
    fun destroy() {
        val vkDevice = device
        for (i in frameResources.indices) {
            val frame = frameResources[i] ?: continue
            frameResources[i] = null
            freeFrameResources(vkDevice, frame)
        }
    }

    private fun resourcesFor(frameIndex: Int, instances: Int = 1): FrameResources {
        require(frameIndex in frameResources.indices) {
            "AlphaInstanceBuffer frame index $frameIndex is outside 0..${frameResources.lastIndex}."
        }
        val current = frameResources[frameIndex]
        if (current != null && current.capacity >= instances) return current
        current?.let(::retireFrameResources)
        var capacity = MIN_CAPACITY.coerceAtMost(maxInstances)
        while (capacity < instances) capacity *= 2
        capacity = capacity.coerceAtMost(maxInstances)
        val byteSize = (capacity * FLOATS_PER_INSTANCE * Float.SIZE_BYTES).toLong()
        val (buffer, memory, mappedPtr) = allocateHostVisibleBuffer(byteSize)
        return FrameResources(
            BufferHandle(buffer),
            DeviceMemoryHandle(memory),
            mappedPtr,
            capacity,
            byteSize,
        ).also { frameResources[frameIndex] = it }
    }

    private fun retireFrameResources(old: FrameResources) {
        val vkDevice = device
        retiredCount += 1L
        graphicsDevice.destroyOrRetire {
            freeFrameResources(vkDevice, old)
        }
    }

    private fun freeFrameResources(vkDevice: Long, frame: FrameResources) {
        if (vkDevice == 0L) return
        VulkanBuffers.vkDestroyBuffer(vkDevice, frame.buffer.handle)
        if (frame.mappedPtr != 0L) VulkanBuffers.vkUnmapMemory(vkDevice, frame.memory.handle)
        VulkanBuffers.vkFreeMemory(vkDevice, frame.memory.handle)
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

    /** Binding index and stride constants for the per-instance colour stream. */
    companion object {
        /**
         * Vertex-buffer binding index of the per-instance colour stream: 2, after the mesh's own
         * buffer at 0 and the model matrices at 1.
         */
        const val INSTANCE_ALPHA_BINDING = 2

        /** Floats per instance: 4, one `vec4` colour. */
        val FLOATS_PER_INSTANCE = GpuDataShape.Vec4.componentCount

        internal const val MIN_CAPACITY = 16
    }
}
