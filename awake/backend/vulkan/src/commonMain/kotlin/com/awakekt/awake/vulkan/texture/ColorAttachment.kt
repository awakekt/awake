/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
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
    usage: Int = VkImageUsageFlagBits2.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
    aspectMask: Int = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
) {
    private var image = 0L
    private var memory = 0L
    var view = 0L
        private set

    /** Bytes of device memory bound to the image, as the driver required. */
    var bytes = 0L
        private set

    init {
        try {
            image = VulkanImages.vkCreateImage(
                graphicsDevice.device,
                VkImageCreateInfo(width, height, format, usage, samples = samples),
            )
            val requirements = VulkanImages.vkGetImageMemoryRequirements(graphicsDevice.device, image)
            val type = VulkanBuffers.findMemoryType(
                graphicsDevice.physicalDevice,
                requirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            )
            memory = VulkanBuffers.vkAllocateMemory(graphicsDevice.device, VkMemoryAllocateInfo(requirements.size, type))
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
