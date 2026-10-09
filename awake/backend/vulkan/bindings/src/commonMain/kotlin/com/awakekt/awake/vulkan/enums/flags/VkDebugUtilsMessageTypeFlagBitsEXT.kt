/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkEnum
import com.awakekt.awake.vulkan.has

/**
 * What a debug utils message is about (`VkDebugUtilsMessageTypeFlagBitsEXT`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkDebugUtilsMessageTypeFlagBitsEXT(override val value: VkFlags) : VkEnum {
    /** An event unrelated to the specification or performance. */
    VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT(0x00000001),

    /** A violation of the specification or a possible mistake. */
    VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT(0x00000002),

    /** A potentially non-optimal use of Vulkan. */
    VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT(0x00000004),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_DEBUG_UTILS_MESSAGE_TYPE_FLAG_BITS_MAX_ENUM_EXT(0x7FFFFFFF),
}
typealias VkDebugUtilsMessageTypeFlagsEXT = VkFlags

/**
 * A display label for this message-type mask: the first of `General`, `Validation` or `Performance`
 * that it has set, or `All` when it has none of those but is not empty. Matching stops at the first
 * hit, so only one name is returned; an empty mask gives an empty string.
 */
val VkDebugUtilsMessageTypeFlagsEXT.formatted: String
    get() {
        val types = mutableSetOf<String>()

        when {
            this has VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT -> types.add(
                "General",
            )

            this has VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT -> types.add(
                "Validation",
            )

            this has VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT -> types.add(
                "Performance",
            )

            this has VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_FLAG_BITS_MAX_ENUM_EXT -> types.add(
                "All",
            )
        }
        return types.joinToString()
    }
