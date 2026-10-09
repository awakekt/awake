/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.flags.VkCommandBufferUsageFlags

/**
 * Parameters for starting to record a command buffer (`VkCommandBufferBeginInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of command buffer usage flags, such as one-time submit.
 * @property pInheritanceInfo The state a secondary command buffer inherits from its primary one;
 * `null` for a primary command buffer.
 */
class VkCommandBufferBeginInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
    val pNext: Any? = null,
    val flags: VkCommandBufferUsageFlags = 0,
    val pInheritanceInfo: Array<VkCommandBufferInheritanceInfo>? = null,
)
