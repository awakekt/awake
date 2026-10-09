/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * Flags that change how a device queue is created (`VkDeviceQueueCreateFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkDeviceQueueCreateFlagBits(val value: Int) {
    /** The queue is a protected queue, able to work on protected memory. */
    VK_DEVICE_QUEUE_CREATE_PROTECTED_BIT(0x00000001),
}

typealias VkDeviceQueueCreateFlags = VkFlags
