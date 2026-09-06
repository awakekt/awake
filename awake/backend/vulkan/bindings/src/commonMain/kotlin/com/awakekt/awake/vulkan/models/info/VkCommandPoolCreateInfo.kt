/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.flags.VkCommandPoolCreateFlags

data class VkCommandPoolCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkCommandPoolCreateFlags = 0,
    val queueFamilyIndex: Int = 0,
)
