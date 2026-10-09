/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * Where an image view component reads its value from (`VkComponentSwizzle`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkComponentSwizzle(val value: Int) {
    /** The component reads its own channel of the image. */
    VK_COMPONENT_SWIZZLE_IDENTITY(0),

    /** The component reads as the constant 0. */
    VK_COMPONENT_SWIZZLE_ZERO(1),

    /** The component reads as the constant 1. */
    VK_COMPONENT_SWIZZLE_ONE(2),

    /** The component reads the image's red channel. */
    VK_COMPONENT_SWIZZLE_R(3),

    /** The component reads the image's green channel. */
    VK_COMPONENT_SWIZZLE_G(4),

    /** The component reads the image's blue channel. */
    VK_COMPONENT_SWIZZLE_B(5),

    /** The component reads the image's alpha channel. */
    VK_COMPONENT_SWIZZLE_A(6),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COMPONENT_SWIZZLE_MAX_ENUM(0x7FFFFFFF),
    ;

    /** Lookup of a constant from its raw integer value. */
    companion object {
        // Helper function to convert integer value to VkComponentSwizzle
        /**
         * Returns the constant whose value is [value], or `null` when none matches.
         *
         * @param value The raw integer value.
         */
        fun fromInt(value: Int) = values().find { it.value == value }
    }
}
