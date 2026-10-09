/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.utils

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.VulkanExtension
import com.awakekt.awake.vulkan.enums.VkPresentModeKHR
import com.awakekt.awake.vulkan.models.VkSurfaceCapabilitiesKHR
import com.awakekt.awake.vulkan.models.VkSurfaceFormatKHR

/**
 * What a surface offers a swapchain on a given physical device.
 *
 * @property capabilities Image counts, extents, transforms and usages the surface supports.
 * @property formats The pixel format and colour space pairs the surface can present.
 * @property presentModes The present modes the surface supports, such as FIFO or mailbox.
 */
data class SwapChainSupportDetails(
    val capabilities: VkSurfaceCapabilitiesKHR,
    val formats: List<VkSurfaceFormatKHR>,
    val presentModes: List<VkPresentModeKHR>,
)

/**
 * Returns whether [physicalDevice] can present to [surface]: it must offer `VK_KHR_swapchain` and
 * report at least one surface format and one present mode.
 *
 * @param physicalDevice The physical device to check.
 * @param surface The surface to present to.
 */
fun isSwapChainSupported(physicalDevice: Long, surface: Long): Boolean {
    var swapChainAdequate = false
    // verify swap chain extension supported
    if (isDeviceExtSupported(physicalDevice, VulkanExtension.VK_KHR_SWAPCHAIN)) {
        val swapChainSupport = querySwapChainSupport(physicalDevice, surface)
        swapChainAdequate =
            swapChainSupport.formats.isNotEmpty() &&
            swapChainSupport.presentModes.isNotEmpty()
    }
    return swapChainAdequate
}

/**
 * Reads everything needed to choose swapchain settings for [surface] on [physicalDevice].
 *
 * @param physicalDevice The physical device to query.
 * @param surface The surface to query; it must not be 0.
 * @return The surface capabilities, formats and present modes.
 */
fun querySwapChainSupport(physicalDevice: Long, surface: Long): SwapChainSupportDetails {
    val capabilities =
        Vulkan.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface)
    val formats = Vulkan.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface)
    val presentModes =
        Vulkan.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface)

    return SwapChainSupportDetails(
        capabilities,
        formats.toList(),
        presentModes.toList(),
    )
}
