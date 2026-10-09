/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * The colour channels a blend attachment writes (`VkColorComponentFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkColorComponentFlagBits(override val value: Int) : VkEnum {
    /** The red channel is written. */
    VK_COLOR_COMPONENT_R_BIT(0x00000001),

    /** The green channel is written. */
    VK_COLOR_COMPONENT_G_BIT(0x00000002),

    /** The blue channel is written. */
    VK_COLOR_COMPONENT_B_BIT(0x00000004),

    /** The alpha channel is written. */
    VK_COLOR_COMPONENT_A_BIT(0x00000008),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COLOR_COMPONENT_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkColorComponentFlags = VkFlags
