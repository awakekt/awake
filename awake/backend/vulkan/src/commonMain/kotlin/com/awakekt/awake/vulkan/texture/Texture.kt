/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.mipChain
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.handles.ImageHandle
import com.awakekt.awake.vulkan.handles.ImageViewHandle
import com.awakekt.awake.vulkan.handles.SamplerHandle
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferImageCopy
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageTiling
import com.awakekt.awake.vulkan.models.info.VkImageType
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSharingMode2

/**
 * Phase 2 (renderer abstraction): owns a single 2D texture's image/view/sampler upload --
 * extracted verbatim from `VulkanApplication`'s `createTextureImage`/`createTextureImageView`/
 * `createTextureSampler` functions and their backing fields. Same staging-buffer pattern as
 * [com.awakekt.awake.vulkan.mesh.Mesh]: a HOST_VISIBLE staging buffer is written,
 * then copied into a DEVICE_LOCAL image via a one-time command buffer.
 *
 * [runOneTimeCommands] is injected for the same reason as `Mesh`: it needs a command pool and
 * graphics queue, neither of which is extracted into a dedicated class yet.
 *
 * Descriptor-set binding (which set/binding index this texture occupies) is a Material/Shader
 * concern, not this class's -- callers read [imageView]/[sampler] to build their own
 * `VkDescriptorImageInfo`.
 */
class Texture(
    graphicsDevice: GraphicsDevice,
    runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
    data: ByteArray,
    width: Int,
    height: Int,
    samplerCreateInfo: VkSamplerCreateInfo = VkSamplerCreateInfo(),
    /** More than one uploads a `VK_IMAGE_VIEW_TYPE_2D_ARRAY`; see [TextureAsset.layerCount]. */
    private val layerCount: Int = 1,
) {
    private val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    var image: ImageHandle = ImageHandle(0)
    var imageMemory: DeviceMemoryHandle = DeviceMemoryHandle(0)
    var imageView: ImageViewHandle = ImageViewHandle(0)
    var sampler: SamplerHandle = SamplerHandle(0)

    init {
        // Neither backend has GPU-side mip generation (no vkCmdBlitImage binding here, no
        // native blit-based mip gen in WebGPU either) -- the whole chain is box-filtered on
        // the CPU once at load time and every level uploaded directly. See MipChain.kt.
        val asset = TextureAsset(data, width, height, layerCount)
        // Array textures ship level 0 only. A chain per layer would need a box filter that
        // respects layer boundaries and interleaved buffer offsets, and the arrays this exists
        // for -- splat layers, lookup tables -- are sampled at an explicit level anyway.
        // ponytail: single-level arrays; add per-layer chains if one ever needs distance filtering.
        val mipLevels = if (layerCount > 1) listOf(asset) else asset.mipChain()
        val combined = ByteArray(mipLevels.sumOf { it.data.size })
        var writeOffset = 0
        val levelOffsets = IntArray(mipLevels.size)
        mipLevels.forEachIndexed { index, level ->
            levelOffsets[index] = writeOffset
            level.data.copyInto(combined, writeOffset)
            writeOffset += level.data.size
        }

        val stagingBuffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = combined.size.toLong(),
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            ),
        )
        val stagingRequirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, stagingBuffer)
        val stagingMemoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            stagingRequirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        val stagingMemory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(
                allocationSize = stagingRequirements.size,
                memoryTypeIndex = stagingMemoryTypeIndex,
            ),
        )
        VulkanBuffers.vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0)
        VulkanBuffers.writeBufferMemoryBytes(device, stagingMemory, 0, combined)

        // The staging buffer/memory must be freed even if image creation, allocation, or the
        // copy/transition commands below throw -- otherwise a failed upload leaks GPU memory
        // silently. The image/imageMemory themselves aren't covered: if the copy fails,
        // Texture's constructor throws and the object never becomes visible to a caller.
        val rawImage: Long
        val rawImageMemory: Long
        try {
            rawImage = VulkanImages.vkCreateImage(
                device,
                VkImageCreateInfo(
                    width = width,
                    height = height,
                    format = VkFormat.VK_FORMAT_R8G8B8A8_UNORM.value,
                    usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSFER_DST_BIT or
                        VkImageUsageFlagBits2.VK_IMAGE_USAGE_SAMPLED_BIT,
                    imageType = VkImageType.VK_IMAGE_TYPE_2D,
                    tiling = VkImageTiling.VK_IMAGE_TILING_OPTIMAL,
                    initialLayout = VkImageLayout2.VK_IMAGE_LAYOUT_UNDEFINED,
                    sharingMode = VkSharingMode2.VK_SHARING_MODE_EXCLUSIVE,
                    mipLevels = mipLevels.size,
                    arrayLayers = layerCount,
                ),
            )
            val imageRequirements = VulkanImages.vkGetImageMemoryRequirements(device, rawImage)
            val imageMemoryTypeIndex = VulkanBuffers.findMemoryType(
                physicalDevice,
                imageRequirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            )
            rawImageMemory = VulkanBuffers.vkAllocateMemory(
                device,
                VkMemoryAllocateInfo(
                    allocationSize = imageRequirements.size,
                    memoryTypeIndex = imageMemoryTypeIndex,
                ),
            )
            VulkanImages.vkBindImageMemory(device, rawImage, rawImageMemory, 0)

            runOneTimeCommands { commandBuffer ->
                VulkanImages.vkTransitionImageLayout(
                    commandBuffer,
                    rawImage,
                    VkImageLayout2.VK_IMAGE_LAYOUT_UNDEFINED,
                    VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    levelCount = mipLevels.size,
                    layerCount = layerCount,
                )
                if (layerCount > 1) {
                    // One copy per layer at level 0. A single copy with layerCount = N would
                    // need every layer contiguous at one offset, which is how `data` is laid
                    // out -- but stating the offset per layer keeps this correct if the packing
                    // ever changes, and costs N commands on an upload that happens once.
                    repeat(layerCount) { layer ->
                        VulkanImages.vkCmdCopyBufferToImage(
                            commandBuffer,
                            stagingBuffer,
                            rawImage,
                            VkBufferImageCopy(
                                imageWidth = width,
                                imageHeight = height,
                                bufferOffset = asset.layerOffset(layer).toLong(),
                                mipLevel = 0,
                                baseArrayLayer = layer,
                                layerCount = 1,
                            ),
                        )
                    }
                } else {
                    mipLevels.forEachIndexed { index, level ->
                        VulkanImages.vkCmdCopyBufferToImage(
                            commandBuffer,
                            stagingBuffer,
                            rawImage,
                            VkBufferImageCopy(
                                imageWidth = level.width,
                                imageHeight = level.height,
                                bufferOffset = levelOffsets[index].toLong(),
                                mipLevel = index,
                            ),
                        )
                    }
                }
                VulkanImages.vkTransitionImageLayout(
                    commandBuffer,
                    rawImage,
                    VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    levelCount = mipLevels.size,
                    layerCount = layerCount,
                )
            }
        } finally {
            VulkanBuffers.vkDestroyBuffer(device, stagingBuffer)
            VulkanBuffers.vkFreeMemory(device, stagingMemory)
        }
        image = ImageHandle(rawImage)
        imageMemory = DeviceMemoryHandle(rawImageMemory)

        imageView = ImageViewHandle(
            Vulkan.vkCreateImageView(
                device,
                VkImageViewCreateInfo(
                    image = rawImage,
                    viewType = if (layerCount > 1) {
                        VkImageViewType.VK_IMAGE_VIEW_TYPE_2D_ARRAY
                    } else {
                        VkImageViewType.VK_IMAGE_VIEW_TYPE_2D
                    },
                    format = VkFormat.VK_FORMAT_R8G8B8A8_UNORM,
                    subresourceRange = VkImageSubresourceRange(
                        aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
                        baseMipLevel = 0,
                        levelCount = mipLevels.size,
                        baseArrayLayer = 0,
                        layerCount = layerCount,
                    ),
                ),
            ),
        )

        // minLod/maxLod always reflect this texture's own real mip count, overriding whatever
        // the caller passed -- the caller can't know the chain length in advance (it depends on
        // width/height, resolved above).
        sampler = SamplerHandle(
            VulkanImages.vkCreateSampler(
                device,
                VkSamplerCreateInfo(
                    magFilter = samplerCreateInfo.magFilter,
                    minFilter = samplerCreateInfo.minFilter,
                    addressModeU = samplerCreateInfo.addressModeU,
                    addressModeV = samplerCreateInfo.addressModeV,
                    addressModeW = samplerCreateInfo.addressModeW,
                    anisotropyEnable = samplerCreateInfo.anisotropyEnable,
                    maxAnisotropy = samplerCreateInfo.maxAnisotropy,
                    borderColor = samplerCreateInfo.borderColor,
                    unnormalizedCoordinates = samplerCreateInfo.unnormalizedCoordinates,
                    mipmapMode = samplerCreateInfo.mipmapMode,
                    minLod = 0f,
                    maxLod = (mipLevels.size - 1).toFloat(),
                ),
            ),
        )
    }

    fun destroy() {
        VulkanImages.vkDestroySampler(device, sampler.handle)
        Vulkan.vkDestroyImageView(device, imageView.handle)
        VulkanImages.vkDestroyImage(device, image.handle)
        VulkanBuffers.vkFreeMemory(device, imageMemory.handle)
    }
}
