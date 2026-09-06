/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkSemaphoreCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkSemaphoreCreateFlags = 0,
)

typealias VkSemaphoreCreateFlags = VkFlags
