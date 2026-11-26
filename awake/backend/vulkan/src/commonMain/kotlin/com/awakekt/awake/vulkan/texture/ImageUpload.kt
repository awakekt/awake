/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.renderer.TextureSampling
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.commands.UploadBatcher
import com.awakekt.awake.vulkan.device.DeviceMemoryAllocation
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.SlabKind
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkBufferImageCopy
import com.awakekt.awake.vulkan.models.info.VkFilter
import com.awakekt.awake.vulkan.models.info.VkImageCreateFlagBits
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageTiling
import com.awakekt.awake.vulkan.models.info.VkImageType
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerMipmapMode
import com.awakekt.awake.vulkan.models.info.VkSharingMode2

/** A sampled image [ImageUpload.upload] made: the raw handles a [Texture] holds. */
internal class UploadedImage(
    val image: Long,
    val memory: Long,
    val view: Long,
    val sampler: Long,
    /** What [sampler] was made from, so it can be made again at another anisotropy (#1660). */
    val samplerInfo: VkSamplerCreateInfo = VkSamplerCreateInfo(),
    val filtering: TextureFiltering = TextureFiltering.Linear,
    val mipLevels: Int = 1,
    /** The base level's size as uploaded: smaller than the source when mip skip left levels out (#1660). */
    val width: Int = 0,
    val height: Int = 0,
    /** Exact uploaded payload size in bytes across all uploaded mip levels and layers. */
    val byteSize: Long = 0L,
    /** Slab or standalone memory allocation backing [image], freed by `Texture.destroy()`. */
    val memoryAllocation: DeviceMemoryAllocation? = null,
    /** Cancellation handle when the initial copy is queued in [UploadBatcher]. */
    val pendingUpload: UploadBatcher.PendingUpload? = null,
)

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
    ): UploadedImage = upload(
        graphicsDevice = graphicsDevice,
        runOneTimeCommands = runOneTimeCommands,
        format = format,
        width = width,
        height = height,
        mipLevels = mipLevels,
        layerCount = layerCount,
        isCubemap = isCubemap,
        byteSize = data.size.toLong(),
        writeStaging = { mappedPtr, stagingOffset ->
            VulkanBuffers.writeMappedMemoryBytes(mappedPtr, stagingOffset, data, 0, data.size)
        },
        regions = regions,
        samplerCreateInfo = samplerCreateInfo,
        filtering = filtering,
    )

    /**
     * Streaming variant of [upload] that writes [byteSize] bytes directly into mapped staging
     * memory through [writeStaging], avoiding an intermediate flattened heap `ByteArray` when
     * uploading multi-level mip chains.
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
        byteSize: Long,
        writeStaging: (mappedPtr: Long, stagingOffset: Long) -> Unit,
        regions: List<Region>,
        samplerCreateInfo: VkSamplerCreateInfo,
        filtering: TextureFiltering,
    ): UploadedImage {
        val device = graphicsDevice.device
        // Whatever exists when the upload doesn't finish is released, so a failed upload leaks no GPU
        // memory: the reusable staging buffer is returned to the pool, and the image, its memory
        // and its view are freed unless returned.
        var image = 0L
        var allocation: DeviceMemoryAllocation? = null
        var pendingUpload: UploadBatcher.PendingUpload? = null
        var view = 0L
        var uploaded: UploadedImage? = null
        try {
            image = VulkanImages.vkCreateImage(device, imageInfo(format, width, height, mipLevels, layerCount, isCubemap))
            val requirements = VulkanImages.vkGetImageMemoryRequirements(device, image)
            val allocated = graphicsDevice.deviceMemoryAllocator.allocate(requirements, SlabKind.Image, image)
            allocation = allocated
            VulkanImages.vkBindImageMemory(device, image, allocated.memory, allocated.offset)
            val batcher = graphicsDevice.activeUploadBatcher?.takeIf { it.isBatching }
            if (batcher != null) {
                val targetImage = image
                pendingUpload = batcher.enqueue(
                    byteSize = byteSize,
                    alignment = IMAGE_STAGING_ALIGNMENT,
                    flushSync = runOneTimeCommands,
                    writeStaging = writeStaging,
                    recordCopy = { commandBuffer, stagingBuffer, stagingOffset ->
                        copy(commandBuffer, stagingBuffer, stagingOffset, targetImage, regions)
                    },
                )
            } else {
                graphicsDevice.stagingBufferPool.withStaging(byteSize) { staging ->
                    writeStaging(staging.mappedPtr, 0L)
                    runOneTimeCommands { commandBuffer -> copy(commandBuffer, staging.buffer, 0L, image, regions) }
                }
            }
            view = view(graphicsDevice, image, format, mipLevels, layerCount, isCubemap)
            uploaded = UploadedImage(
                image,
                allocated.memory,
                view,
                sampler(graphicsDevice, samplerCreateInfo, filtering, mipLevels),
                samplerCreateInfo,
                filtering,
                mipLevels,
                width,
                height,
                byteSize = byteSize,
                memoryAllocation = allocated,
                pendingUpload = pendingUpload,
            )
            return uploaded
        } finally {
            if (uploaded == null) {
                pendingUpload?.cancel()
                if (view != 0L) Vulkan.vkDestroyImageView(device, view)
                if (image != 0L) VulkanImages.vkDestroyImage(device, image)
                if (allocation != null) graphicsDevice.deviceMemoryAllocator.free(allocation)
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

    // The image is moved to TRANSFER_DST across every level and layer the regions fill, then to
    // SHADER_READ_ONLY once they're copied.
    private fun copy(commandBuffer: Long, buffer: Long, stagingOffset: Long, image: Long, regions: List<Region>) {
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
                    bufferOffset = stagingOffset + region.bufferOffset,
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
    // chain's length in advance. Anisotropy above 1 reaches only a fully linear, mipmapped texture,
    // as TextureSampling decides for every backend (#1660).
    internal fun sampler(
        graphicsDevice: GraphicsDevice,
        info: VkSamplerCreateInfo,
        filtering: TextureFiltering,
        mipLevels: Int,
        anisotropy: Int = 1,
    ): Long {
        val nearest = filtering == TextureFiltering.Nearest
        val samples = if (mipLevels > 1) TextureSampling.anisotropyFor(filtering, anisotropy) else 1
        return VulkanImages.vkCreateSampler(
            graphicsDevice.device,
            VkSamplerCreateInfo(
                magFilter = if (nearest) VkFilter.VK_FILTER_NEAREST else info.magFilter,
                minFilter = if (nearest) VkFilter.VK_FILTER_NEAREST else info.minFilter,
                addressModeU = info.addressModeU,
                addressModeV = info.addressModeV,
                addressModeW = info.addressModeW,
                anisotropyEnable = if (samples > 1) true else info.anisotropyEnable,
                maxAnisotropy = if (samples > 1) samples.toFloat() else info.maxAnisotropy,
                borderColor = info.borderColor,
                unnormalizedCoordinates = info.unnormalizedCoordinates,
                mipmapMode = if (nearest) VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST else info.mipmapMode,
                minLod = 0f,
                maxLod = (mipLevels - 1).toFloat(),
            ),
        )
    }

    /** 16-byte alignment satisfies every RGBA8 / BCn / ETC2 / ASTC texel-block offset requirement. */
    private const val IMAGE_STAGING_ALIGNMENT: Long = 16L
}
