/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkEnum

/**
 * Flags that change how a query is begun (`VkQueryControlFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkQueryControlFlagBits(override val value: Int) : VkEnum {
    /** An occlusion query returns an exact sample count rather than any non-zero value. */
    VK_QUERY_CONTROL_PRECISE_BIT(0x00000001),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_QUERY_CONTROL_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkQueryControlFlags = VkFlags
