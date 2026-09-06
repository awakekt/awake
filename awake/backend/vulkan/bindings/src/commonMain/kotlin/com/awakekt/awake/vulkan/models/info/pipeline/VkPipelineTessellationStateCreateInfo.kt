/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkPipelineTessellationStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineTessellationStateCreateFlags = 0,
    val patchControlPoints: Int = 0,
)

typealias VkPipelineTessellationStateCreateFlags = VkFlags
