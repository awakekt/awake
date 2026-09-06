/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkFenceCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkFenceCreateFlags = 0,
)

typealias VkFenceCreateFlags = VkFlags
