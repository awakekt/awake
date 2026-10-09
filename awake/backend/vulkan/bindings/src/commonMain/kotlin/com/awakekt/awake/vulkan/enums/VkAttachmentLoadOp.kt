/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * What happens to an attachment's contents at the start of a subpass (`VkAttachmentLoadOp`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkAttachmentLoadOp(val value: Int) {
    /** The existing contents are preserved and loaded. */
    LOAD(0),

    /** The attachment is cleared to the clear value. */
    CLEAR(1),

    /** The previous contents need not be preserved; they are undefined afterwards. */
    DONT_CARE(2),

    /** The attachment is neither read nor cleared, and its contents are left as they are. */
    NONE_EXT(1000400000),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    MAX_ENUM(0x7FFFFFFF),
}
