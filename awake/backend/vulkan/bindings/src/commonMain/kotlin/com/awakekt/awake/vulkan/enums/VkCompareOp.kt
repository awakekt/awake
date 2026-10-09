/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How two values are compared in depth, stencil and sampler tests (`VkCompareOp`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkCompareOp(val value: Int) {
    /** The test never passes. */
    VK_COMPARE_OP_NEVER(0),

    /** The test passes when the incoming value is less than the stored value. */
    VK_COMPARE_OP_LESS(1),

    /** The test passes when the incoming value equals the stored value. */
    VK_COMPARE_OP_EQUAL(2),

    /** The test passes when the incoming value is less than or equal to the stored value. */
    VK_COMPARE_OP_LESS_OR_EQUAL(3),

    /** The test passes when the incoming value is greater than the stored value. */
    VK_COMPARE_OP_GREATER(4),

    /** The test passes when the incoming value differs from the stored value. */
    VK_COMPARE_OP_NOT_EQUAL(5),

    /** The test passes when the incoming value is greater than or equal to the stored value. */
    VK_COMPARE_OP_GREATER_OR_EQUAL(6),

    /** The test always passes. */
    VK_COMPARE_OP_ALWAYS(7),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COMPARE_OP_MAX_ENUM(0x7FFFFFFF),
}
