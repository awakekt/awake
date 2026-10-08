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

/** Staging allocations belong to a frame slot and survive until that slot's next fence. */
internal class TextureUploads(private val graphicsDevice: GraphicsDevice) : TextureUploadRecorder {
    private data class Staging(val buffer: Long, val memory: Long)
    private val pending = HashMap<Int, MutableList<Staging>>()
    private var frameIndex = 0
    private var commandBuffer = 0L
    private val device get() = graphicsDevice.device

    fun beginFrame(frameIndex: Int, commandBuffer: Long) {
        pending.remove(frameIndex)?.forEach(::release)
        this.frameIndex = frameIndex
        this.commandBuffer = commandBuffer
    }

    @Suppress("TooGenericExceptionCaught") // Release native staging before propagating any upload failure.
    override fun write(texture: WritableTexture, region: TextureRegion) {
        region.validate(texture)
        val image = texture as Texture
        val buffer = VulkanBuffers.vkCreateBuffer(device, VkBufferCreateInfo(size = region.data.size.toLong(), usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT))
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
            VulkanBuffers.writeBufferMemoryBytes(device, memory, 0, region.data)
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image.image.handle,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                layerCount = image.layerCount,
            )
            com.awakekt.awake.vulkan.gen.VulkanTextureRegions.copy(commandBuffer, buffer, image.image.handle, region.layer, region.x, region.y, region.width, region.height)
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image.image.handle,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                layerCount = image.layerCount,
            )
            pending.getOrPut(frameIndex) { mutableListOf() } += Staging(buffer, memory)
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
        pending.values.forEach { it.forEach(::release) }
        pending.clear()
    }
}
