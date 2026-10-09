/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * Whether a resource is used by one queue family at a time or by several concurrently
 * (`VkSharingMode`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkSharingMode(val value: Int) {
    /** The resource is owned by one queue family at a time; ownership is transferred explicitly. */
    VK_SHARING_MODE_EXCLUSIVE(0),

    /** The resource can be used by several queue families at once, with no ownership transfer. */
    VK_SHARING_MODE_CONCURRENT(1),
}
