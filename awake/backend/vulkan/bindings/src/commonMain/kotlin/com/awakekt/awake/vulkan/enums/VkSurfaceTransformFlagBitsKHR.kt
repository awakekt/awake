/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

// Representing VkSurfaceTransformFlagBitsKHR as an enum class
/**
 * A rotation or mirroring applied to a swapchain image before presentation
 * (`VkSurfaceTransformFlagBitsKHR`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkSurfaceTransformFlagBitsKHR(val value: Int) {
    /** The image is presented unchanged. */
    VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR(0x00000001),

    /** The image is rotated 90 degrees clockwise. */
    VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR(0x00000002),

    /** The image is rotated 180 degrees clockwise. */
    VK_SURFACE_TRANSFORM_ROTATE_180_BIT_KHR(0x00000004),

    /** The image is rotated 270 degrees clockwise. */
    VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR(0x00000008),

    /** The image is mirrored horizontally. */
    VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_BIT_KHR(0x00000010),

    /** The image is mirrored horizontally, then rotated 90 degrees clockwise. */
    VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_90_BIT_KHR(0x00000020),

    /** The image is mirrored horizontally, then rotated 180 degrees clockwise. */
    VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_180_BIT_KHR(0x00000040),

    /** The image is mirrored horizontally, then rotated 270 degrees clockwise. */
    VK_SURFACE_TRANSFORM_HORIZONTAL_MIRROR_ROTATE_270_BIT_KHR(0x00000080),

    /** The transform is decided outside Vulkan, by native window system code. */
    VK_SURFACE_TRANSFORM_INHERIT_BIT_KHR(0x00000100),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_SURFACE_TRANSFORM_FLAG_BITS_MAX_ENUM_KHR(0x7FFFFFFF),
}

typealias VkSurfaceTransformFlagsKHR = VkFlags
