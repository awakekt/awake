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

/**
 * Phase 1d descriptor-set API surface, generated via jni-binding-generator (see
 * [VulkanBuffers] for the package-scoping rationale shared by every object in this
 * package). Deliberately scoped to a single descriptor set per allocate/update/bind call
 * (no `descriptorSetCount`/array-of-sets support) -- jni-binding-generator's array support
 * is proven for *struct fields* (Phase 1a) but not yet exercised here for function-level
 * array params/returns of handles, and the MVP's textured-cube milestone only ever needs
 * one descriptor set per frame-in-flight, so there's no real need to risk that untested path.
 */
expect object VulkanDescriptors {
    /**
     * Creates a descriptor set layout, the shape of the resource bindings a shader reads from one
     * set.
     *
     * @param device The logical device to create the layout on.
     * @param createInfo The bindings: slot, descriptor type, count and shader stages.
     * @return The new `VkDescriptorSetLayout` handle.
     */
    fun vkCreateDescriptorSetLayout(device: Long, createInfo: VkDescriptorSetLayoutCreateInfo): Long

    /**
     * Destroys a descriptor set layout.
     *
     * @param device The logical device that created the layout.
     * @param layout The layout to destroy.
     */
    fun vkDestroyDescriptorSetLayout(device: Long, layout: Long)

    /**
     * Creates a pool that descriptor sets are allocated from.
     *
     * @param device The logical device to create the pool on.
     * @param createInfo The maximum set count and the number of descriptors of each type.
     * @return The new `VkDescriptorPool` handle.
     */
    fun vkCreateDescriptorPool(device: Long, createInfo: VkDescriptorPoolCreateInfo): Long

    /**
     * Destroys a descriptor pool together with every set allocated from it.
     *
     * @param device The logical device that created the pool.
     * @param pool The pool to destroy.
     */
    fun vkDestroyDescriptorPool(device: Long, pool: Long)

    /**
     * Allocates a single descriptor set with the given layout from a pool.
     *
     * @param device The logical device that owns the pool.
     * @param pool The pool to allocate from; it must have room for one more set.
     * @param layout The layout that shapes the new set.
     * @return The new `VkDescriptorSet` handle. It is freed with its pool.
     */
    fun vkAllocateDescriptorSet(device: Long, pool: Long, layout: Long): Long

    /**
     * Writes a buffer into one binding of a descriptor set. The set must not be in use by pending
     * GPU work.
     *
     * @param device The logical device that owns the set.
     * @param dstSet The set to update.
     * @param dstBinding The binding index within the set.
     * @param descriptorType The descriptor type as a plain `Int` from
     * [com.awakekt.awake.vulkan.models.info.VkDescriptorType], such as a uniform or storage buffer.
     * @param bufferInfo The buffer, byte offset and range to bind.
     */
    fun vkUpdateDescriptorSetBuffer(
        device: Long,
        dstSet: Long,
        dstBinding: Int,
        descriptorType: Int,
        bufferInfo: VkDescriptorBufferInfo,
    )

    /**
     * Writes an image view, a sampler, or both into one binding of a descriptor set. The set must
     * not be in use by pending GPU work.
     *
     * @param device The logical device that owns the set.
     * @param dstSet The set to update.
     * @param dstBinding The binding index within the set.
     * @param descriptorType The descriptor type as a plain `Int` from
     * [com.awakekt.awake.vulkan.models.info.VkDescriptorType], such as a sampled image or sampler.
     * @param imageInfo The sampler, image view and layout to bind; a zero handle is ignored by
     * types that do not use it.
     */
    fun vkUpdateDescriptorSetImage(
        device: Long,
        dstSet: Long,
        dstBinding: Int,
        descriptorType: Int,
        imageInfo: VkDescriptorImageInfo,
    )

    /**
     * Records binding of one descriptor set to a graphics pipeline layout.
     *
     * @param commandBuffer The command buffer being recorded.
     * @param pipelineLayout The layout of the pipeline the set is used with.
     * @param firstSet The set index to bind to.
     * @param descriptorSet The set to bind.
     */
    fun vkCmdBindDescriptorSet(
        commandBuffer: Long,
        pipelineLayout: Long,
        firstSet: Int,
        descriptorSet: Long,
    )
}
