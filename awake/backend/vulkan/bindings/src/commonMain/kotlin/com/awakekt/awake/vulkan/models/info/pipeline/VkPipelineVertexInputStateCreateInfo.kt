/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.VkVertexInputRate

data class VkVertexInputBindingDescription(
    val binding: Int,
    val stride: Int,
    val inputRate: VkVertexInputRate,
)

data class VkVertexInputAttributeDescription(
    val location: Int,
    val binding: Int,
    val format: VkFormat,
    val offset: Int,
)

class VkPipelineVertexInputStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineVertexInputStateCreateFlags = 0,
    @VkArray(sizeAlias = "vertexBindingDescriptionCount")
    val pVertexBindingDescriptions: Array<VkVertexInputBindingDescription>? = null,
    @VkArray(sizeAlias = "vertexAttributeDescriptionCount")
    val pVertexAttributeDescriptions: Array<VkVertexInputAttributeDescription>? = null,
)

typealias VkPipelineVertexInputStateCreateFlags = VkFlags
