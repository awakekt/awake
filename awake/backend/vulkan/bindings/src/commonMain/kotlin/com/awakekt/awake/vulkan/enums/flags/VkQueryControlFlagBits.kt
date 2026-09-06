/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkEnum

enum class VkQueryControlFlagBits(override val value: Int) : VkEnum {
    VK_QUERY_CONTROL_PRECISE_BIT(0x00000001),
    VK_QUERY_CONTROL_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkQueryControlFlags = VkFlags
