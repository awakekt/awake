/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.texture.TextureRegion
import com.awakekt.awake.render.texture.TextureUploadRecorder
import com.awakekt.awake.render.texture.WritableTexture
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/** Smallest staging buffer worth keeping for reuse. */
private const val MIN_STAGING_BYTES = 64L * 1024

/**
 * Staging buffers belong to a frame slot until that slot's next fence, then return to a shared
 * pool, so steady streaming allocates nothing. Every image written in a frame changes layout once:
 * [write] moves it to transfer on first use, and [endFrame] returns all of them to shader reads.
 */
internal class TextureUploads(private val graphicsDevice: GraphicsDevice) : TextureUploadRecorder {
    private class Staging(val buffer: Long, val memory: Long, val size: Long)
    private val inUse = HashMap<Int, MutableList<Staging>>()
    private val free = mutableListOf<Staging>()
    private val touched = mutableListOf<Texture>()
    private var frameIndex = 0
    private var commandBuffer = 0L
    private val device get() = graphicsDevice.device

    fun beginFrame(frameIndex: Int, commandBuffer: Long) {
        inUse.remove(frameIndex)?.let(free::addAll)
        touched.clear()
        this.frameIndex = frameIndex
        this.commandBuffer = commandBuffer
    }

    override fun write(texture: WritableTexture, region: TextureRegion) {
        region.validate(texture)
        val image = texture as Texture
        val staging = acquire(region.data.size.toLong())
        inUse.getOrPut(frameIndex) { mutableListOf() } += staging
        VulkanBuffers.writeBufferMemoryBytes(device, staging.memory, 0, region.data)
        // Regions written in one frame are disjoint, so copies into one image share a single transition.
        if (touched.none { it === image }) {
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image.image.handle,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                layerCount = image.layerCount,
            )
            touched += image
        }
        com.awakekt.awake.vulkan.gen.VulkanTextureRegions.copy(commandBuffer, staging.buffer, image.image.handle, region.layer, region.x, region.y, region.width, region.height)
    }

    /** Returns every image written since [beginFrame] to shader reads, before any pass samples it. */
    fun endFrame() {
        for (image in touched) {
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image.image.handle,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                layerCount = image.layerCount,
            )
        }
        touched.clear()
    }

    /** The smallest pooled buffer that fits, or a new one rounded up so later frames can reuse it. */
    private fun acquire(size: Long): Staging {
        var best = -1
        for (index in free.indices) {
            if (free[index].size >= size && (best < 0 || free[index].size < free[best].size)) best = index
        }
        if (best >= 0) return free.removeAt(best)
        var capacity = MIN_STAGING_BYTES
        while (capacity < size) capacity *= 2
        return allocate(capacity)
    }

    @Suppress("TooGenericExceptionCaught") // Release native staging before propagating any allocation failure.
    private fun allocate(size: Long): Staging {
        val buffer = VulkanBuffers.vkCreateBuffer(device, VkBufferCreateInfo(size = size, usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT))
        var memory = 0L
        try {
            val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
            val type = VulkanBuffers.findMemoryType(
                graphicsDevice.physicalDevice,
                requirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            )
            memory = VulkanBuffers.vkAllocateMemory(device, VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = type))
            VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
            return Staging(buffer, memory, size)
        } catch (failure: Throwable) {
            VulkanBuffers.vkDestroyBuffer(device, buffer)
            if (memory != 0L) VulkanBuffers.vkFreeMemory(device, memory)
            throw failure
        }
    }

    private fun release(staging: Staging) {
        VulkanBuffers.vkDestroyBuffer(device, staging.buffer)
        VulkanBuffers.vkFreeMemory(device, staging.memory)
    }

    fun destroy() {
        inUse.values.forEach { it.forEach(::release) }
        inUse.clear()
        free.forEach(::release)
        free.clear()
    }
}
