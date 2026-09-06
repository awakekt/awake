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

class VkPushConstantRange(
    val stageFlags: VkShaderStageFlags = 0,
    val offset: Int = 0,
    val size: Int = 0,
)
typealias VkShaderStageFlags = VkFlags

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
