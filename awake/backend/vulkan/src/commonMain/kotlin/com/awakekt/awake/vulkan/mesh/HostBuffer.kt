/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.allocateHostVisibleBufferMemory
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo

/** A persistently mapped HOST_VISIBLE | HOST_COHERENT buffer and its memory: the CPU writes it and the GPU reads it, no staging copy. */
internal class HostBuffer(val buffer: Long, val memory: Long, val mappedPtr: Long = 0L) {
    /** Destroys the buffer and frees its memory. Call once the GPU is done with it. */
    fun free(graphicsDevice: GraphicsDevice) {
        VulkanBuffers.vkDestroyBuffer(graphicsDevice.device, buffer)
        if (mappedPtr != 0L) VulkanBuffers.vkUnmapMemory(graphicsDevice.device, memory)
        VulkanBuffers.vkFreeMemory(graphicsDevice.device, memory)
    }

    companion object {
        /** Creates a [byteSize]-byte buffer for [usage] in host-visible, coherent memory. */
        fun allocate(
            graphicsDevice: GraphicsDevice,
            byteSize: Long,
            usage: Int,
            persistentMap: Boolean = true,
        ): HostBuffer {
            val device = graphicsDevice.device
            val buffer = VulkanBuffers.vkCreateBuffer(device, VkBufferCreateInfo(size = byteSize, usage = usage))
            val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
            val memory = graphicsDevice.allocateHostVisibleBufferMemory(requirements)
            VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
            val mappedPtr = if (persistentMap) VulkanBuffers.vkMapMemory(device, memory, 0L, requirements.size) else 0L
            return HostBuffer(buffer, memory, mappedPtr)
        }
    }
}

/**
 * Float arrays of every power-of-two size, each made once and kept: what a range is copied into when
 * the native write takes a whole array. Shared by a renderer's dynamic meshes; not thread-safe.
 */
internal class PowerOfTwoFloats {
    private val bySize = arrayOfNulls<FloatArray>(Int.SIZE_BITS)

    /** The kept array of the smallest power-of-two size of at least [count] floats. */
    fun atLeast(count: Int): FloatArray {
        val size = ceil(count)
        val slot = Int.SIZE_BITS - 1 - size.countLeadingZeroBits()
        return bySize[slot] ?: FloatArray(size).also { bySize[slot] = it }
    }

    companion object {
        /** The smallest power of two of at least [count], which is positive. */
        fun ceil(count: Int): Int {
            require(count > 0) { "count must be positive, not $count." }
            val highest = count.takeHighestOneBit()
            return if (highest == count) count else highest shl 1
        }
    }
}
