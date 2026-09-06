/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkPrimitiveTopology
import com.awakekt.awake.vulkan.enums.VkStructureType

data class VkPipelineInputAssemblyStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineInputAssemblyStateCreateFlags = 0,
    val topology: VkPrimitiveTopology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
    val primitiveRestartEnable: VkBool32 = false,
)

typealias VkPipelineInputAssemblyStateCreateFlags = VkFlags
