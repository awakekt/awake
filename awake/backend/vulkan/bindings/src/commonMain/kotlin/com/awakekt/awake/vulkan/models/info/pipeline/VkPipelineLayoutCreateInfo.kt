/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * A range of push constants and the stages that read it (`VkPushConstantRange`).
 *
 * @property stageFlags A mask of the shader stages that access the range.
 * @property offset The start of the range, in bytes.
 * @property size The size of the range, in bytes.
 */
class VkPushConstantRange(
    val stageFlags: VkShaderStageFlags = 0,
    val offset: Int = 0,
    val size: Int = 0,
)
typealias VkShaderStageFlags = VkFlags

/**
 * Parameters for creating a pipeline layout (`VkPipelineLayoutCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of pipeline layout creation flags; 0 for none.
 * @property pSetLayouts The descriptor set layouts, in set-number order.
 * @property pushConstantRangeCount The number of push constant ranges.
 * @property pPushConstantRanges The push constant ranges.
 */
class VkPipelineLayoutCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineLayoutCreateFlags = 0,
    @field:VkHandleRef("VkDescriptorSetLayout")
    @VkArray(sizeAlias = "setLayoutCount")
    val pSetLayouts: Array<VkDescriptorSetLayout>? = null, // Optional
    val pushConstantRangeCount: Int = 0, // Optional
    val pPushConstantRanges: Array<VkPushConstantRange>? = null, // Optional
)

typealias VkPipelineLayout = VkHandle
typealias VkDescriptorSetLayout = VkHandle
typealias VkPipelineLayoutCreateFlags = VkFlags
