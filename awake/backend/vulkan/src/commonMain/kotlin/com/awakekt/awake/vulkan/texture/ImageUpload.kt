/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferImageCopy
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkFilter
import com.awakekt.awake.vulkan.models.info.VkImageCreateFlagBits
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageTiling
import com.awakekt.awake.vulkan.models.info.VkImageType
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerMipmapMode
import com.awakekt.awake.vulkan.models.info.VkSharingMode2

/** A sampled image [ImageUpload.upload] made: the raw handles a [Texture] holds. */
internal class UploadedImage(val image: Long, val memory: Long, val view: Long, val sampler: Long)

/**
 * Uploading a sampled image of any format with its mips, whether RGBA8 or block-compressed: a
 * HOST_VISIBLE staging buffer copied into a DEVICE_LOCAL image by one-time commands, then an image
 * view and a sampler over every level.
 */
internal object ImageUpload {

    /**
     * One copy into the image: [width] x [height] texels of level [mipLevel] and layer [layer], read
     * from [bufferOffset] in the packed data. A block format's extent is still in texels; a level
     * smaller than a block is one partial block.
     */
    class Region(val width: Int, val height: Int, val bufferOffset: Long, val mipLevel: Int, val layer: Int = 0)

    /**
     * An image of [format], [width] x [height] with [mipLevels] levels and [layerCount] layers, filled
     * from [data] by [regions]. [filtering] Nearest forces nearest sampling; the sampler's LOD range
     * is the image's own levels, whatever [samplerCreateInfo] says.
     */
    @Suppress("LongParameterList") // One parameter per property of the image; grouping them hides which.
    fun upload(
        graphicsDevice: GraphicsDevice,
        runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
        format: VkFormat,
        width: Int,
        height: Int,
        mipLevels: Int,
        layerCount: Int,
        isCubemap: Boolean,
        data: ByteArray,
        regions: List<Region>,
        samplerCreateInfo: VkSamplerCreateInfo,
        filtering: TextureFiltering,
    ): UploadedImage {
        val device = graphicsDevice.device
        // Whatever exists when the upload doesn't finish is released, so a failed upload leaks no GPU
        // memory: the staging buffer always, and the image, its memory and its view unless returned.
        val (stagingBuffer, stagingMemory) = staging(graphicsDevice, data)
        var image = 0L
        var memory = 0L
        var view = 0L
        var uploaded: UploadedImage? = null
        try {
            image = VulkanImages.vkCreateImage(device, imageInfo(format, width, height, mipLevels, layerCount, isCubemap))
            memory = deviceLocalMemory(graphicsDevice, image)
            VulkanImages.vkBindImageMemory(device, image, memory, 0)
            runOneTimeCommands { commandBuffer -> copy(commandBuffer, stagingBuffer, image, regions) }
            view = view(graphicsDevice, image, format, mipLevels, layerCount, isCubemap)
            uploaded = UploadedImage(image, memory, view, sampler(graphicsDevice, samplerCreateInfo, filtering, mipLevels))
            return uploaded
        } finally {
            VulkanBuffers.vkDestroyBuffer(device, stagingBuffer)
            VulkanBuffers.vkFreeMemory(device, stagingMemory)
            if (uploaded == null) {
                if (view != 0L) Vulkan.vkDestroyImageView(device, view)
                if (image != 0L) VulkanImages.vkDestroyImage(device, image)
                if (memory != 0L) VulkanBuffers.vkFreeMemory(device, memory)
            }
        }
    }

    @Suppress("LongParameterList") // The image's format, size, levels and layers, each its own property.
    private fun imageInfo(format: VkFormat, width: Int, height: Int, mipLevels: Int, layerCount: Int, isCubemap: Boolean) = VkImageCreateInfo(
        width = width,
        height = height,
        format = format.value,
        usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSFER_DST_BIT or VkImageUsageFlagBits2.VK_IMAGE_USAGE_SAMPLED_BIT,
        imageType = VkImageType.VK_IMAGE_TYPE_2D,
        tiling = VkImageTiling.VK_IMAGE_TILING_OPTIMAL,
        initialLayout = VkImageLayout2.VK_IMAGE_LAYOUT_UNDEFINED,
        sharingMode = VkSharingMode2.VK_SHARING_MODE_EXCLUSIVE,
        mipLevels = mipLevels,
        arrayLayers = layerCount,
        flags = if (isCubemap) VkImageCreateFlagBits.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT else 0,
    )

    /** DEVICE_LOCAL memory the size [image] needs, not yet bound. */
    private fun deviceLocalMemory(graphicsDevice: GraphicsDevice, image: Long): Long {
        val requirements = VulkanImages.vkGetImageMemoryRequirements(graphicsDevice.device, image)
        return VulkanBuffers.vkAllocateMemory(
            graphicsDevice.device,
            VkMemoryAllocateInfo(
                allocationSize = requirements.size,
                memoryTypeIndex = VulkanBuffers.findMemoryType(
                    graphicsDevice.physicalDevice,
                    requirements.memoryTypeBits,
                    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                ),
            ),
        )
    }

    /** A HOST_VISIBLE staging buffer holding [data], with its memory. */
    private fun staging(graphicsDevice: GraphicsDevice, data: ByteArray): Pair<Long, Long> {
        val device = graphicsDevice.device
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(size = data.size.toLong(), usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT),
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
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
        VulkanBuffers.writeBufferMemoryBytes(device, memory, 0, data)
        return buffer to memory
    }

    // The image is moved to TRANSFER_DST across every level and layer the regions fill, then to
    // SHADER_READ_ONLY once they're copied.
    private fun copy(commandBuffer: Long, buffer: Long, image: Long, regions: List<Region>) {
        val mipLevels = regions.maxOf { it.mipLevel } + 1
        val layerCount = regions.maxOf { it.layer } + 1
        VulkanImages.vkTransitionImageLayout(
            commandBuffer,
            image,
            VkImageLayout2.VK_IMAGE_LAYOUT_UNDEFINED,
            VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            levelCount = mipLevels,
            layerCount = layerCount,
        )
        regions.forEach { region ->
            VulkanImages.vkCmdCopyBufferToImage(
                commandBuffer,
                buffer,
                image,
                VkBufferImageCopy(
                    imageWidth = region.width,
                    imageHeight = region.height,
                    bufferOffset = region.bufferOffset,
                    mipLevel = region.mipLevel,
                    baseArrayLayer = region.layer,
                    layerCount = 1,
                ),
            )
        }
        VulkanImages.vkTransitionImageLayout(
            commandBuffer,
            image,
            VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            levelCount = mipLevels,
            layerCount = layerCount,
        )
    }

    @Suppress("LongParameterList") // The view's format, levels and layers, each its own property.
    private fun view(graphicsDevice: GraphicsDevice, image: Long, format: VkFormat, mipLevels: Int, layerCount: Int, isCubemap: Boolean): Long =
        Vulkan.vkCreateImageView(
            graphicsDevice.device,
            VkImageViewCreateInfo(
                image = image,
                viewType = when {
                    isCubemap -> VkImageViewType.VK_IMAGE_VIEW_TYPE_CUBE
                    layerCount > 1 -> VkImageViewType.VK_IMAGE_VIEW_TYPE_2D_ARRAY
                    else -> VkImageViewType.VK_IMAGE_VIEW_TYPE_2D
                },
                format = format,
                subresourceRange = VkImageSubresourceRange(
                    aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
                    baseMipLevel = 0,
                    levelCount = mipLevels,
                    baseArrayLayer = 0,
                    layerCount = layerCount,
                ),
            ),
        )

    // minLod and maxLod are the image's own levels, whatever the caller passed: it can't know the
    // chain's length in advance.
    private fun sampler(graphicsDevice: GraphicsDevice, info: VkSamplerCreateInfo, filtering: TextureFiltering, mipLevels: Int): Long {
        val nearest = filtering == TextureFiltering.Nearest
        return VulkanImages.vkCreateSampler(
            graphicsDevice.device,
            VkSamplerCreateInfo(
                magFilter = if (nearest) VkFilter.VK_FILTER_NEAREST else info.magFilter,
                minFilter = if (nearest) VkFilter.VK_FILTER_NEAREST else info.minFilter,
                addressModeU = info.addressModeU,
                addressModeV = info.addressModeV,
                addressModeW = info.addressModeW,
                anisotropyEnable = info.anisotropyEnable,
                maxAnisotropy = info.maxAnisotropy,
                borderColor = info.borderColor,
                unnormalizedCoordinates = info.unnormalizedCoordinates,
                mipmapMode = if (nearest) VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST else info.mipmapMode,
                minLod = 0f,
                maxLod = (mipLevels - 1).toFloat(),
            ),
        )
    }
}
