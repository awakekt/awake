/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.VkFlags

/**
 * Flags that change how a dependency between pipeline stages applies (`VkDependencyFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkDependencyFlagBits(val value: Int) {
    /**
     * The dependency is framebuffer-local: it applies per region rather than to the whole
     * attachment.
     */
    VK_DEPENDENCY_BY_REGION_BIT(0x00000001),

    /** The dependency is device-local, between the devices of a device group. */
    VK_DEPENDENCY_DEVICE_GROUP_BIT(0x00000004),

    /** The dependency is view-local: each view depends only on the same view of the source. */
    VK_DEPENDENCY_VIEW_LOCAL_BIT(0x00000002),

    /** Alias of [VK_DEPENDENCY_VIEW_LOCAL_BIT]; both names carry the same value. */
    VK_DEPENDENCY_VIEW_LOCAL_BIT_KHR(VK_DEPENDENCY_VIEW_LOCAL_BIT.value),

    /** Alias of [VK_DEPENDENCY_DEVICE_GROUP_BIT]; both names carry the same value. */
    VK_DEPENDENCY_DEVICE_GROUP_BIT_KHR(VK_DEPENDENCY_DEVICE_GROUP_BIT.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_DEPENDENCY_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkDependencyFlags = VkFlags
