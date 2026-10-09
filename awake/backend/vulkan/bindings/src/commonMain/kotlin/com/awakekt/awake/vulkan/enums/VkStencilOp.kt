/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * What happens to a stencil value after a stencil or depth test (`VkStencilOp`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkStencilOp(val value: Int) {
    /** The value is kept. */
    VK_STENCIL_OP_KEEP(0),

    /** The value is set to 0. */
    VK_STENCIL_OP_ZERO(1),

    /** The value is set to the reference value. */
    VK_STENCIL_OP_REPLACE(2),

    /** The value is incremented, clamping at the maximum. */
    VK_STENCIL_OP_INCREMENT_AND_CLAMP(3),

    /** The value is decremented, clamping at 0. */
    VK_STENCIL_OP_DECREMENT_AND_CLAMP(4),

    /** The value's bits are inverted. */
    VK_STENCIL_OP_INVERT(5),

    /** The value is incremented, wrapping to 0 at the maximum. */
    VK_STENCIL_OP_INCREMENT_AND_WRAP(6),

    /** The value is decremented, wrapping to the maximum below 0. */
    VK_STENCIL_OP_DECREMENT_AND_WRAP(7),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_STENCIL_OP_MAX_ENUM(0x7FFFFFFF),
}
