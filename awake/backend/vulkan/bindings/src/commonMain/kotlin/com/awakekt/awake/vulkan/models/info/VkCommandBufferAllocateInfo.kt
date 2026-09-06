/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkCommandBufferLevel
import com.awakekt.awake.vulkan.enums.VkStructureType

data class VkCommandBufferAllocateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
    val pNext: Any? = null,
    @field:VkHandleRef("VkCommandPool")
    val commandPool: VkHandle = 0,
    val level: VkCommandBufferLevel = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
    val commandBufferCount: Int = 1,
)
