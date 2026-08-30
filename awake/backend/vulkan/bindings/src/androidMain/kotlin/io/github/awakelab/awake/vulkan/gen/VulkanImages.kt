/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.gen

import io.github.awakelab.awake.vulkan.models.VkMemoryRequirements
import io.github.awakelab.awake.vulkan.JniNative
import io.github.awakelab.awake.vulkan.models.info.VkBufferImageCopy
import io.github.awakelab.awake.vulkan.models.info.VkImageCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkSamplerCreateInfo

actual object VulkanImages {
    actual external fun vkCreateImage(device: Long, createInfo: VkImageCreateInfo): Long
    actual external fun vkDestroyImage(device: Long, image: Long)
    actual external fun vkGetImageMemoryRequirements(device: Long, image: Long): VkMemoryRequirements
    actual external fun vkBindImageMemory(device: Long, image: Long, memory: Long, memoryOffset: Long)
    actual external fun vkCreateSampler(device: Long, createInfo: VkSamplerCreateInfo): Long
    actual external fun vkDestroySampler(device: Long, sampler: Long)
    @JniNative("awake_vulkan_images_transition_image_layout")
    actual external fun vkTransitionImageLayout(
        commandBuffer: Long,
        image: Long,
        oldLayout: Int,
        newLayout: Int,
        levelCount: Int,
        layerCount: Int,
    )

    actual external fun vkCmdCopyBufferToImage(
        commandBuffer: Long,
        srcBuffer: Long,
        dstImage: Long,
        copy: VkBufferImageCopy,
    )

    actual external fun vkCmdCopyImageToBuffer(
        commandBuffer: Long,
        srcImage: Long,
        dstBuffer: Long,
        copy: VkBufferImageCopy,
    )
}
