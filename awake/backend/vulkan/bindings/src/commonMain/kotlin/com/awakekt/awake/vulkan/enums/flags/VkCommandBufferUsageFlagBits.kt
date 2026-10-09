/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.enums.VkEnum

/**
 * Hints about how a command buffer will be used (`VkCommandBufferUsageFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkCommandBufferUsageFlagBits(override val value: Int) : VkEnum {
    /** The command buffer is submitted once and then reset or freed. */
    VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT(0x00000001),

    /** A secondary command buffer lies entirely within a render pass. */
    VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT(0x00000002),

    /** The command buffer may be resubmitted while a previous submission is still pending. */
    VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT(0x00000004),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COMMAND_BUFFER_USAGE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkCommandBufferUsageFlags = Int
