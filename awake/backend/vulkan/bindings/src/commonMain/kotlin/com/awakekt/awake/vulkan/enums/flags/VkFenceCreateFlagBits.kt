/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

/**
 * Flags that change how a fence is created (`VkFenceCreateFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkFenceCreateFlagBits(val value: Int) {
    /** The fence starts in the signalled state. */
    VK_FENCE_CREATE_SIGNALED_BIT(0x00000001),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_FENCE_CREATE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}
