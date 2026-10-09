/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.enums.VkDeviceQueueCreateFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating the queues of one queue family with a device (`VkDeviceQueueCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of queue creation flags, such as protected.
 * @property queueFamilyIndex The queue family to create queues from.
 * @property queueCount The number of queues to create.
 * @property pQueuePriorities A priority from 0.0 to 1.0 for each queue; the array must hold
 * [queueCount] values.
 */
class VkDeviceQueueCreateInfo(
    var sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
    var pNext: Any? = null,
    var flags: VkDeviceQueueCreateFlags = 0, // VkDeviceQueueCreateFlagBits
    var queueFamilyIndex: Int = 0,
    var queueCount: Int = 0,
    @field:VkArray
    var pQueuePriorities: FloatArray = floatArrayOf(1f),
)
