/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.utils

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkQueueFlagBits
import com.awakekt.awake.vulkan.has

/**
 * The queue family indices a renderer needs from a physical device.
 *
 * @property graphicsFamily The index of a family that supports graphics, or `null` if none does.
 * @property presentFamily The index of a family that can present to the surface, or `null` if none
 * can or no surface was given.
 */
data class QueueFamilyIndices(
    var graphicsFamily: Int? = null,
    var presentFamily: Int? = null,
) {
    /** Returns whether both a graphics family and a present family were found. */
    fun isComplete(): Boolean = graphicsFamily != null && presentFamily != null
}

/**
 * Finds the queue families of [physicalDevice] that can run graphics work and present to [surface].
 *
 * The last matching family of each kind wins. A [surface] of 0 means a headless device:
 * presentation support is not queried and [QueueFamilyIndices.presentFamily] stays `null`.
 *
 * @param physicalDevice The physical device to inspect.
 * @param surface The surface to present to, or 0 for none.
 * @return The family indices; a field is `null` when no family qualifies.
 */
fun findQueueFamilies(physicalDevice: Long, surface: Long): QueueFamilyIndices {
    val queueFamilyProperties =
        Vulkan.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice)
    val indices = QueueFamilyIndices()
    queueFamilyProperties.forEachIndexed { index, queueFamily ->
        if (queueFamily.queueFlags has VkQueueFlagBits.VK_QUEUE_GRAPHICS_BIT) {
            indices.graphicsFamily = index
        }
        // surface == 0L (VK_NULL_HANDLE) means a headless GraphicsDevice (see
        // GraphicsDevice.createHeadless) -- querying vkGetPhysicalDeviceSurfaceSupportKHR
        // against VK_NULL_HANDLE is undefined behavior per spec, so skip present-family
        // detection entirely; headless callers never need presentFamily anyway.
        if (surface != 0L &&
            Vulkan.vkGetPhysicalDeviceSurfaceSupportKHR(
                physicalDevice,
                index,
                surface,
            )
        ) {
            indices.presentFamily = index
        }
    }
    return indices
}
