/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

/**
 * Flags that change how a command pool behaves (`VkCommandPoolCreateFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkCommandPoolCreateFlagBits(val value: Int) {
    /** Command buffers from the pool are short-lived and rerecorded often. */
    VK_COMMAND_POOL_CREATE_TRANSIENT_BIT(0x00000001),

    /** Command buffers from the pool can be reset individually. */
    VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT(0x00000002),

    /** Command buffers from the pool are protected command buffers. */
    VK_COMMAND_POOL_CREATE_PROTECTED_BIT(0x00000004),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COMMAND_POOL_CREATE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkCommandPoolCreateFlags = Int
