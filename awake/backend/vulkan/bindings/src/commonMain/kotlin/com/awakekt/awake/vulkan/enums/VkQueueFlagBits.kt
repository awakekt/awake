/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

// Define the enum class VkQueueFlagBits
/**
 * The kinds of work a queue family supports (`VkQueueFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkQueueFlagBits(val value: Int) {
    /** The queues support graphics operations. */
    VK_QUEUE_GRAPHICS_BIT(0x00000001),

    /** The queues support compute operations. */
    VK_QUEUE_COMPUTE_BIT(0x00000002),

    /** The queues support transfer operations. */
    VK_QUEUE_TRANSFER_BIT(0x00000004),

    /** The queues support sparse memory management operations. */
    VK_QUEUE_SPARSE_BINDING_BIT(0x00000008),

    /** The queues support protected memory operations. */
    VK_QUEUE_PROTECTED_BIT(0x00000010),

    /** The queues support video decode operations. */
    VK_QUEUE_VIDEO_DECODE_BIT_KHR(0x00000020),

    /** The queues support video encode operations. */
    VK_QUEUE_VIDEO_ENCODE_BIT_KHR(0x00000040),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_QUEUE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkQueueFlags = VkFlags
