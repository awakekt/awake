/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.flags.VkCommandPoolCreateFlags

/**
 * Parameters for creating a command pool (`VkCommandPoolCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of command pool creation flags, such as per-buffer reset.
 * @property queueFamilyIndex The queue family that the pool's command buffers are submitted to.
 */
data class VkCommandPoolCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkCommandPoolCreateFlags = 0,
    val queueFamilyIndex: Int = 0,
)
