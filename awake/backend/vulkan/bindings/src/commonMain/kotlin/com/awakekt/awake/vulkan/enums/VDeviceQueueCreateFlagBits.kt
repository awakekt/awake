/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

enum class VkDeviceQueueCreateFlagBits(val value: Int) {
    VK_DEVICE_QUEUE_CREATE_PROTECTED_BIT(0x00000001),
}

typealias VkDeviceQueueCreateFlags = VkFlags
