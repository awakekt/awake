/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkEnum

/**
 * Flags that change how a command buffer is reset (`VkCommandBufferResetFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkCommandBufferResetFlagBits(override val value: Int) : VkEnum {
    /** The command buffer's memory is returned to its pool instead of being kept for reuse. */
    VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT(0x00000001),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COMMAND_BUFFER_RESET_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkCommandBufferResetFlags = VkFlags
