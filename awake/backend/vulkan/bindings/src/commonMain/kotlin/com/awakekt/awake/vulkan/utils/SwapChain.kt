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

data class SwapChainSupportDetails(
    val capabilities: VkSurfaceCapabilitiesKHR,
    val formats: List<VkSurfaceFormatKHR>,
    val presentModes: List<VkPresentModeKHR>,
)

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
