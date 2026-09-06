/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkPipelineCacheCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: Int = 0,
    @field:VkArray("initialDataSize")
    val pInitialData: Array<Any>? = null,
)
