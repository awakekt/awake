/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating a semaphore (`VkSemaphoreCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 */
class VkSemaphoreCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkSemaphoreCreateFlags = 0,
)

typealias VkSemaphoreCreateFlags = VkFlags
