/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/** A HOST_VISIBLE | HOST_COHERENT buffer and its memory: the CPU writes it and the GPU reads it, no staging copy. */
internal class HostBuffer(val buffer: Long, val memory: Long) {
    /** Destroys the buffer and frees its memory. Call once the GPU is done with it. */
    fun free(graphicsDevice: GraphicsDevice) {
        VulkanBuffers.vkDestroyBuffer(graphicsDevice.device, buffer)
        VulkanBuffers.vkFreeMemory(graphicsDevice.device, memory)
    }

    companion object {
        /** Creates a [byteSize]-byte buffer for [usage] in host-visible, coherent memory. */
        fun allocate(graphicsDevice: GraphicsDevice, byteSize: Long, usage: Int): HostBuffer {
            val device = graphicsDevice.device
            val buffer = VulkanBuffers.vkCreateBuffer(device, VkBufferCreateInfo(size = byteSize, usage = usage))
            val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
            val memoryTypeIndex = VulkanBuffers.findMemoryType(
                graphicsDevice.physicalDevice,
                requirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            )
            val memory = VulkanBuffers.vkAllocateMemory(
                device,
                VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
            )
            VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
            return HostBuffer(buffer, memory)
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
