/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.enums

import io.github.awakelab.awake.vulkan.VkFlags

enum class VkDeviceQueueCreateFlagBits(val value: Int) {
    VK_DEVICE_QUEUE_CREATE_PROTECTED_BIT(0x00000001),
}

typealias VkDeviceQueueCreateFlags = VkFlags
