/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * Which triangle faces rasterization discards (`VkCullModeFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkCullModeFlagBits(val value: Int) {
    /** No faces are culled. */
    VK_CULL_MODE_NONE(0),

    /** Front-facing triangles are culled. */
    VK_CULL_MODE_FRONT_BIT(0x00000001),

    /** Back-facing triangles are culled. */
    VK_CULL_MODE_BACK_BIT(0x00000002),

    /** All triangles are culled, so only points and lines are drawn. */
    VK_CULL_MODE_FRONT_AND_BACK(0x00000003),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_CULL_MODE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkCullModeFlags = VkFlags
