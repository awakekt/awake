/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkDynamicState
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkPipelineDynamicStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineDynamicStateCreateFlags = 0,
    @field:VkArray(sizeAlias = "dynamicStateCount")
    val pDynamicStates: Array<VkDynamicState> = emptyArray(),
)

typealias VkPipelineDynamicStateCreateFlags = VkFlags
