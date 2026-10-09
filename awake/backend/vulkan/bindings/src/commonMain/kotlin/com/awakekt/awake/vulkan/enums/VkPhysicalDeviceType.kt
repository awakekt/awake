/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The broad kind of a physical device (`VkPhysicalDeviceType`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPhysicalDeviceType(val value: Int) {
    /** The device does not match any other type. */
    VK_PHYSICAL_DEVICE_TYPE_OTHER(0),

    /** A GPU integrated with the host processor, usually sharing system memory. */
    VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU(1),

    /** A separate GPU reached over an interconnect, usually with its own memory. */
    VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU(2),

    /** A virtual GPU node in a virtualization environment. */
    VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU(3),

    /** A software implementation running on the host processor. */
    VK_PHYSICAL_DEVICE_TYPE_CPU(4),
}
