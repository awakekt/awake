/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * Whether a command buffer is submitted directly or executed from another (`VkCommandBufferLevel`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkCommandBufferLevel(val value: Int) {
    /**
     * A command buffer that can be submitted to a queue and can execute secondary command buffers.
     */
    VK_COMMAND_BUFFER_LEVEL_PRIMARY(0),

    /** A command buffer that cannot be submitted directly and is executed from a primary one. */
    VK_COMMAND_BUFFER_LEVEL_SECONDARY(1),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COMMAND_BUFFER_LEVEL_MAX_ENUM(0x7FFFFFFF),
}
