/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The dimensionality a view presents an image as (`VkImageViewType`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkImageViewType(val value: Int) {
    /** A one-dimensional image. */
    VK_IMAGE_VIEW_TYPE_1D(0),

    /** A two-dimensional image. */
    VK_IMAGE_VIEW_TYPE_2D(1),

    /** A three-dimensional image. */
    VK_IMAGE_VIEW_TYPE_3D(2),

    /** A cube map: six 2D faces. */
    VK_IMAGE_VIEW_TYPE_CUBE(3),

    /** An array of one-dimensional images. */
    VK_IMAGE_VIEW_TYPE_1D_ARRAY(4),

    /** An array of two-dimensional images. */
    VK_IMAGE_VIEW_TYPE_2D_ARRAY(5),

    /** An array of cube maps. */
    VK_IMAGE_VIEW_TYPE_CUBE_ARRAY(6),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_IMAGE_VIEW_TYPE_MAX_ENUM(0x7FFFFFFF),
    ;

    /** Lookup of a constant from its raw integer value. */
    companion object {
        // Helper function to convert integer value to VkImageViewType
        /**
         * Returns the constant whose value is [value], or `null` when none matches.
         *
         * @param value The raw integer value.
         */
        fun fromInt(value: Int) = values().find { it.value == value }
    }
}
