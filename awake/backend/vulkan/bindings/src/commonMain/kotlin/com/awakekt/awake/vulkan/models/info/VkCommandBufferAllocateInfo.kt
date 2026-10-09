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

/**
 * Parameters for allocating a command buffer from a pool (`VkCommandBufferAllocateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property commandPool The raw handle of the pool to allocate from.
 * @property level Whether the buffer is a primary or a secondary command buffer.
 * @property commandBufferCount The number of command buffers to allocate; the binding returns one
 * handle, so use 1.
 */
data class VkCommandBufferAllocateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
    val pNext: Any? = null,
    @field:VkHandleRef("VkCommandPool")
    val commandPool: VkHandle = 0,
    val level: VkCommandBufferLevel = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
    val commandBufferCount: Int = 1,
)
