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

// Phase 1b (desktop native build) has not landed yet — see docs/mvp-plan.md.
/**
 * Desktop JVM actual of [VulkanDescriptors]. Each function is a JNI `external` declaration backed
 * by the generated wrapper in the `awake-vulkan` library: a zero handle throws
 * [IllegalStateException] and a failed Vulkan call throws a
 * [com.awakekt.awake.vulkan.utils.VkResultException]. The library is loaded by
 * [com.awakekt.awake.vulkan.Vulkan], so use that first.
 */
actual object VulkanDescriptors {
    /**
     * Creates a descriptor set layout, the shape of the resource bindings a shader reads from one
     * set.
     */
    actual external fun vkCreateDescriptorSetLayout(
        device: Long,
        createInfo: VkDescriptorSetLayoutCreateInfo,
    ): Long

    /** Destroys a descriptor set layout. */
    actual external fun vkDestroyDescriptorSetLayout(device: Long, layout: Long)

    /** Creates a pool that descriptor sets are allocated from. */
    actual external fun vkCreateDescriptorPool(device: Long, createInfo: VkDescriptorPoolCreateInfo): Long

    /** Destroys a descriptor pool together with every set allocated from it. */
    actual external fun vkDestroyDescriptorPool(device: Long, pool: Long)

    /** Allocates a single descriptor set with the given layout from a pool. */
    actual external fun vkAllocateDescriptorSet(device: Long, pool: Long, layout: Long): Long

    /**
     * Writes a buffer into one binding of a descriptor set. The set must not be in use by pending
     * GPU work.
     */
    actual external fun vkUpdateDescriptorSetBuffer(
        device: Long,
        dstSet: Long,
        dstBinding: Int,
        descriptorType: Int,
        bufferInfo: VkDescriptorBufferInfo,
    )

    /**
     * Writes an image view, a sampler, or both into one binding of a descriptor set. The set must
     * not be in use by pending GPU work.
     */
    actual external fun vkUpdateDescriptorSetImage(
        device: Long,
        dstSet: Long,
        dstBinding: Int,
        descriptorType: Int,
        imageInfo: VkDescriptorImageInfo,
    )

    /** Records binding of one descriptor set to a graphics pipeline layout. */
    actual external fun vkCmdBindDescriptorSet(
        commandBuffer: Long,
        pipelineLayout: Long,
        firstSet: Int,
        descriptorSet: Long,
    )
}
