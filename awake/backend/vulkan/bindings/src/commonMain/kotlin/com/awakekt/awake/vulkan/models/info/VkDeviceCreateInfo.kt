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

/**
 * Parameters for creating a logical device (`VkDeviceCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property pQueueCreateInfos The queues to create along with the device.
 * @property ppEnabledLayerNames Device layers to enable; deprecated, so leave `null`.
 * @property ppEnabledExtensionNames The names of the device extensions to enable.
 * @property pEnabledFeatures The physical device features to enable.
 */
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
