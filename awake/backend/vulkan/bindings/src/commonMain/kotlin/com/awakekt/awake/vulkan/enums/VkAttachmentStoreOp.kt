/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * What happens to an attachment's contents at the end of a subpass (`VkAttachmentStoreOp`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkAttachmentStoreOp(val value: Int) {
    /** The rendered contents are written back to memory. */
    STORE(0),

    /** The contents need not be kept; they are undefined afterwards. */
    DONT_CARE(1),

    /** The attachment is not written, so its previous contents are left as they are. */
    NONE(1000301000),

    /** Alias of [NONE]; both names carry the same value. */
    NONE_KHR(NONE.value),

    /** Alias of [NONE]; both names carry the same value. */
    NONE_QCOM(NONE.value),

    /** Alias of [NONE]; both names carry the same value. */
    NONE_EXT(NONE.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    MAX_ENUM(0x7FFFFFFF),
}
