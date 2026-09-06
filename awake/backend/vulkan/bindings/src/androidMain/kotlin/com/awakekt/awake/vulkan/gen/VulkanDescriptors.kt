/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.models.info.VkDescriptorBufferInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorImageInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo

actual object VulkanDescriptors {
    actual external fun vkCreateDescriptorSetLayout(
        device: Long,
        createInfo: VkDescriptorSetLayoutCreateInfo,
    ): Long

    actual external fun vkDestroyDescriptorSetLayout(device: Long, layout: Long)
    actual external fun vkCreateDescriptorPool(device: Long, createInfo: VkDescriptorPoolCreateInfo): Long
    actual external fun vkDestroyDescriptorPool(device: Long, pool: Long)
    actual external fun vkAllocateDescriptorSet(device: Long, pool: Long, layout: Long): Long
    actual external fun vkUpdateDescriptorSetBuffer(
        device: Long,
        dstSet: Long,
        dstBinding: Int,
        descriptorType: Int,
        bufferInfo: VkDescriptorBufferInfo,
    )

    actual external fun vkUpdateDescriptorSetImage(
        device: Long,
        dstSet: Long,
        dstBinding: Int,
        descriptorType: Int,
        imageInfo: VkDescriptorImageInfo,
    )

    actual external fun vkCmdBindDescriptorSet(
        commandBuffer: Long,
        pipelineLayout: Long,
        firstSet: Int,
        descriptorSet: Long,
    )
}
