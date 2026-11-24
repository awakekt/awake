/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.allocateDeviceImageMemory
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/**
 * Render-pass-only image storage, never sampled directly: multisampled colour resolved by the render
 * pass, or a depth attachment when [usage] and [aspectMask] say so.
 */
@Suppress("TooGenericExceptionCaught") // Every native-allocation failure must release earlier handles before propagating.
internal class ColorAttachment(
    private val graphicsDevice: GraphicsDevice,
    width: Int,
    height: Int,
    val format: Int,
    val samples: Int,
    usage: Int = VkImageUsageFlagBits2.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or
        VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT,
    aspectMask: Int = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
) {
    private var image = 0L
    private var memory = 0L
    var view = 0L
        private set

    /** Bytes of device memory bound to the image, as the driver required. */
    var bytes = 0L
        private set

    /** Whether this attachment was backed by `VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT` tile memory. */
    internal var lazilyAllocated = false
        private set

    init {
        try {
            val effectiveUsage = usage or VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT
            image = VulkanImages.vkCreateImage(
                graphicsDevice.device,
                VkImageCreateInfo(
                    width,
                    height,
                    format,
                    effectiveUsage,
                    samples = samples,
                ),
            )
            val requirements = VulkanImages.vkGetImageMemoryRequirements(graphicsDevice.device, image)
            val lazyType = findLazyAttachmentMemoryTypeOrNegative(graphicsDevice.physicalDevice, requirements.memoryTypeBits)
            memory = if (lazyType >= 0) {
                lazilyAllocated = true
                VulkanBuffers.vkAllocateMemory(graphicsDevice.device, VkMemoryAllocateInfo(requirements.size, lazyType))
            } else {
                graphicsDevice.allocateDeviceImageMemory(image, requirements)
            }
            bytes = requirements.size
            VulkanImages.vkBindImageMemory(graphicsDevice.device, image, memory, 0)
            view = Vulkan.vkCreateImageView(
                graphicsDevice.device,
                VkImageViewCreateInfo(
                    image = image,
                    format = VkFormat.entries.first { it.value == format },
                    subresourceRange = VkImageSubresourceRange(aspectMask = aspectMask),
                ),
            )
        } catch (failure: Throwable) {
            destroy()
            throw failure
        }
    }

    fun destroy() {
        if (view != 0L) Vulkan.vkDestroyImageView(graphicsDevice.device, view)
        if (image != 0L) VulkanImages.vkDestroyImage(graphicsDevice.device, image)
        if (memory != 0L) VulkanBuffers.vkFreeMemory(graphicsDevice.device, memory)
        view = 0L
        image = 0L
        memory = 0L
    }
}

/**
 * Finds a `DEVICE_LOCAL | LAZILY_ALLOCATED` memory type for transient tile-only render-pass
 * attachments on mobile TBDR GPUs, returning `-1` when the physical device exposes none (desktop
 * discrete GPUs and software rasterizers).
 */
internal fun findLazyAttachmentMemoryTypeOrNegative(physicalDevice: Long, memoryTypeBits: Int): Int =
    try {
        VulkanBuffers.findMemoryType(
            physicalDevice,
            memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT,
        )
    } catch (_: IllegalStateException) {
        -1
    }

/**
 * Picks `DEVICE_LOCAL | LAZILY_ALLOCATED` when available for a transient attachment, otherwise
 * falls back to plain `DEVICE_LOCAL`.
 */
internal fun findTransientAttachmentMemoryType(physicalDevice: Long, memoryTypeBits: Int): Int {
    val lazy = findLazyAttachmentMemoryTypeOrNegative(physicalDevice, memoryTypeBits)
    return if (lazy >= 0) {
        lazy
    } else {
        VulkanBuffers.findMemoryType(
            physicalDevice,
            memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
        )
    }
}
