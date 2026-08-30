/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models.info

import io.github.awakelab.awake.vulkan.VkArray
import io.github.awakelab.awake.vulkan.enums.VkDeviceQueueCreateFlags
import io.github.awakelab.awake.vulkan.enums.VkStructureType

class VkDeviceQueueCreateInfo(
    var sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
    var pNext: Any? = null,
    var flags: VkDeviceQueueCreateFlags = 0, // VkDeviceQueueCreateFlagBits
    var queueFamilyIndex: Int = 0,
    var queueCount: Int = 0,
    @field:VkArray
    var pQueuePriorities: FloatArray = floatArrayOf(1f),
)
