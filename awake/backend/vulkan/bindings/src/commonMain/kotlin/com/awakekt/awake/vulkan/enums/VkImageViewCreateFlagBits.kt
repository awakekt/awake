/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * Flags that change how an image view is created (`VkImageViewCreateFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkImageViewCreateFlagBits(val value: Int) {
    /**
     * The fragment density map is read by the device at the fragment density process stage, so
     * earlier work in the same submission may write it.
     */
    VK_IMAGE_VIEW_CREATE_FRAGMENT_DENSITY_MAP_DYNAMIC_BIT_EXT(0x00000001),

    /**
     * The fragment density map is not read by the host when recording ends, so it may still change
     * until the work is submitted.
     */
    VK_IMAGE_VIEW_CREATE_FRAGMENT_DENSITY_MAP_DEFERRED_BIT_EXT(0x00000002),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_IMAGE_VIEW_CREATE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkImageViewCreateFlags = VkFlags
