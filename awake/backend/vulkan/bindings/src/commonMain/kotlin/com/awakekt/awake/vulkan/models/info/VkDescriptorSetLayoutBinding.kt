/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags

/**
 * `descriptorType`/`stageFlags` are modeled as plain `Int` rather than jni-binding-generator
 * enum fields -- see the Phase 1d enum-marshalling hazard note in docs/mvp-plan.md. Both
 * VkDescriptorType and VkShaderStageFlagBits are extension-bearing in the Vulkan spec, so
 * ordinal-based marshalling would be unsafe long-term even though today's values happen to
 * line up. Use [VkDescriptorType] and Awake's existing `VkShaderStageFlagBits.value` for
 * these fields.
 *
 * @property binding The binding number the shader uses for this resource.
 * @property descriptorType The kind of descriptor at the binding, as a plain `Int` from the
 * descriptor type constants.
 * @property descriptorCount The number of descriptors at the binding; more than 1 makes it an
 * array.
 * @property stageFlags A mask of the shader stages that can access the binding.
 */
class VkDescriptorSetLayoutBinding(
    val binding: Int,
    val descriptorType: Int,
    val descriptorCount: Int = 1,
    val stageFlags: VkShaderStageFlags = 0,
)

typealias VkShaderStageFlags = VkFlags

/**
 * Descriptor types as plain `Int` constants (`VkDescriptorType`).
 */
object VkDescriptorType {
    /** A sampler, with no image. */
    const val VK_DESCRIPTOR_TYPE_SAMPLER = 0

    /** A sampler and an image view bound together. */
    const val VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER = 1

    /** An image view that a shader samples through a separate sampler. */
    const val VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE = 2

    /** An image view a shader reads and writes directly. */
    const val VK_DESCRIPTOR_TYPE_STORAGE_IMAGE = 3

    /** A buffer view a shader reads as formatted texels. */
    const val VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER = 4

    /** A buffer view a shader reads and writes as formatted texels. */
    const val VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER = 5

    /** A buffer a shader reads as a uniform block. */
    const val VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER = 6

    /** A buffer a shader reads and writes as a storage block. */
    const val VK_DESCRIPTOR_TYPE_STORAGE_BUFFER = 7

    /** A uniform buffer whose offset is supplied when the descriptor set is bound. */
    const val VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC = 8

    /** A storage buffer whose offset is supplied when the descriptor set is bound. */
    const val VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC = 9

    /** An image view read as an input attachment within a render pass. */
    const val VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT = 10
}

/**
 * Parameters for creating a descriptor set layout (`VkDescriptorSetLayoutCreateInfo`).
 *
 * @property flags Layout creation flags; 0 for none.
 * @property pBindings The bindings of the layout.
 */
class VkDescriptorSetLayoutCreateInfo(
    val flags: Int = 0,
    @VkArray(sizeAlias = "bindingCount")
    val pBindings: Array<VkDescriptorSetLayoutBinding>? = null,
)

/**
 * How many descriptors of one type a descriptor pool can hold (`VkDescriptorPoolSize`).
 *
 * @property type The descriptor type, as a plain `Int` from the descriptor type constants.
 * @property descriptorCount The number of descriptors of that type the pool can allocate.
 */
class VkDescriptorPoolSize(
    val type: Int,
    val descriptorCount: Int,
)

/**
 * Parameters for creating a descriptor pool (`VkDescriptorPoolCreateInfo`).
 *
 * @property maxSets The most descriptor sets that can be allocated from the pool.
 * @property flags Pool creation flags; 0 for none.
 * @property pPoolSizes How many descriptors of each type the pool holds.
 */
class VkDescriptorPoolCreateInfo(
    val maxSets: Int,
    val flags: Int = 0,
    @VkArray(sizeAlias = "poolSizeCount")
    val pPoolSizes: Array<VkDescriptorPoolSize>? = null,
)

/**
 * A buffer range to write into a descriptor (`VkDescriptorBufferInfo`).
 *
 * @property buffer The raw handle of the buffer.
 * @property range The size in bytes of the range the descriptor covers.
 * @property offset The byte offset of the range from the start of the buffer.
 */
class VkDescriptorBufferInfo(
    val buffer: Long,
    val range: Long,
    val offset: Long = 0,
)
