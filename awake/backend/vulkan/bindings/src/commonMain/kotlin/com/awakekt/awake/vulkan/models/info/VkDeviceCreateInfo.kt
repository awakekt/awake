/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceFeatures

class VkDeviceCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkDeviceCreateFlags = 0,
    @field:VkArray(sizeAlias = "queueCreateInfoCount")
    val pQueueCreateInfos: Array<VkDeviceQueueCreateInfo> = emptyArray(),
    @field:VkArray(sizeAlias = "enabledLayerCount")
    val ppEnabledLayerNames: Array<String>? = null,
    @field:VkArray(sizeAlias = "enabledExtensionCount")
    val ppEnabledExtensionNames: Array<String>? = null,
    @field:VkArray
    val pEnabledFeatures: Array<VkPhysicalDeviceFeatures> = emptyArray(),
)
typealias VkDeviceCreateFlags = VkFlags
