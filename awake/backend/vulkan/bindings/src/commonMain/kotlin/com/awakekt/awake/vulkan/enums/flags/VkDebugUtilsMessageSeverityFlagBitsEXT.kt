/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.VkFlags

/**
 * How severe a debug utils message is (`VkDebugUtilsMessageSeverityFlagBitsEXT`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkDebugUtilsMessageSeverityFlagBitsEXT(val value: VkFlags) {
    /** Diagnostic message from the loader, layers or drivers. */
    VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT(0x00000001),

    /** Informational message, such as the creation of a resource. */
    VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT(0x00000010),

    /** Behavior that is not necessarily an error but is very likely a bug or non-optimal. */
    VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT(0x00000100),

    /** Behavior that is invalid and may crash. */
    VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT(0x00001000),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_DEBUG_UTILS_MESSAGE_SEVERITY_FLAG_BITS_MAX_ENUM_EXT(0x7FFFFFFF),
}

typealias VkDebugUtilsMessageSeverityFlagsEXT = VkFlags

/**
 * A short display label for this severity: `Error`, `Warning`, `Info` or `Verbose`. The sentinel
 * reads `Debug`.
 */
val VkDebugUtilsMessageSeverityFlagBitsEXT.formatted
    get() = when (this) {
        VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT -> "Error"
        VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT -> "Warning"
        VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT -> "Info"
        VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT -> "Verbose"
        VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_FLAG_BITS_MAX_ENUM_EXT -> "Debug"
    }
