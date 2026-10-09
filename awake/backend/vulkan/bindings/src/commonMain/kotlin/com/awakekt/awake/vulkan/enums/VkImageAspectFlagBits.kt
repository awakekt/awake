/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * Which aspects of an image a view, barrier or copy touches (`VkImageAspectFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkImageAspectFlagBits(val value: Int) {
    /** The colour aspect. */
    VK_IMAGE_ASPECT_COLOR_BIT(0x00000001),

    /** The depth aspect. */
    VK_IMAGE_ASPECT_DEPTH_BIT(0x00000002),

    /** The stencil aspect. */
    VK_IMAGE_ASPECT_STENCIL_BIT(0x00000004),

    /** The sparse-resource metadata aspect. */
    VK_IMAGE_ASPECT_METADATA_BIT(0x00000008),

    /** The first plane of a multi-planar image. */
    VK_IMAGE_ASPECT_PLANE_0_BIT(0x00000010),

    /** The second plane of a multi-planar image. */
    VK_IMAGE_ASPECT_PLANE_1_BIT(0x00000020),

    /** The third plane of a multi-planar image. */
    VK_IMAGE_ASPECT_PLANE_2_BIT(0x00000040),

    /** Memory plane 0 of an image created with a DRM format modifier. */
    VK_IMAGE_ASPECT_MEMORY_PLANE_0_BIT_EXT(0x00000080),

    /** Memory plane 1 of an image created with a DRM format modifier. */
    VK_IMAGE_ASPECT_MEMORY_PLANE_1_BIT_EXT(0x00000100),

    /** Memory plane 2 of an image created with a DRM format modifier. */
    VK_IMAGE_ASPECT_MEMORY_PLANE_2_BIT_EXT(0x00000200),

    /** Memory plane 3 of an image created with a DRM format modifier. */
    VK_IMAGE_ASPECT_MEMORY_PLANE_3_BIT_EXT(0x00000400),

    /** No aspect; the empty mask. */
    VK_IMAGE_ASPECT_NONE_KHR(0),

    /** Alias of [VK_IMAGE_ASPECT_PLANE_0_BIT]; both names carry the same value. */
    VK_IMAGE_ASPECT_PLANE_0_BIT_KHR(VK_IMAGE_ASPECT_PLANE_0_BIT.value),

    /** Alias of [VK_IMAGE_ASPECT_PLANE_1_BIT]; both names carry the same value. */
    VK_IMAGE_ASPECT_PLANE_1_BIT_KHR(VK_IMAGE_ASPECT_PLANE_1_BIT.value),

    /** Alias of [VK_IMAGE_ASPECT_PLANE_2_BIT]; both names carry the same value. */
    VK_IMAGE_ASPECT_PLANE_2_BIT_KHR(VK_IMAGE_ASPECT_PLANE_2_BIT.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_IMAGE_ASPECT_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
    ;

    /** Lookup of a constant from its raw integer value. */
    companion object {
        // Helper function to convert integer value to VkImageAspectFlagBits
        /**
         * Returns the first constant whose value is [value], or `null` when none matches. Aliases
         * share a value, so the first listed wins.
         *
         * @param value The raw integer value.
         */
        fun fromInt(value: Int) = values().find { it.value == value }
    }
}

typealias VkImageAspectFlags = VkFlags
