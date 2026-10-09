/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How the commands of a subpass are recorded (`VkSubpassContents`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkSubpassContents(override val value: Int) : VkEnum {
    /** The commands are recorded inline in the primary command buffer. */
    VK_SUBPASS_CONTENTS_INLINE(0),

    /** The commands come from secondary command buffers executed from the primary one. */
    VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS(1),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_SUBPASS_CONTENTS_MAX_ENUM(0x7FFFFFFF),
}
