/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.gen.VulkanWindow
import com.awakekt.awake.vulkan.models.VkExtent2D

/**
 * Creates the `VkSurfaceKHR` for a GLFW window through
 * [com.awakekt.awake.vulkan.gen.VulkanWindow.glfwCreateWindowSurface].
 *
 * @param instance The Vulkan instance to create the surface on.
 * @param window The GLFW window handle, as a `Long`.
 * @return The new `VkSurfaceKHR` handle.
 * @throws ClassCastException If [window] is not a `Long`.
 */
actual fun createSurface(instance: Long, window: Any): Long =
    VulkanWindow.glfwCreateWindowSurface(instance, window as Long)

/**
 * Returns the GLFW window's framebuffer size in device pixels, which is what a variable-extent
 * swapchain is built from.
 *
 * @param window The GLFW window handle, as a `Long`.
 * @throws ClassCastException If [window] is not a `Long`.
 */
actual fun surfaceFramebufferExtent(window: Any): VkExtent2D? {
    val handle = window as Long
    return VkExtent2D(
        width = VulkanWindow.glfwGetFramebufferWidth(handle),
        height = VulkanWindow.glfwGetFramebufferHeight(handle),
    )
}

/**
 * Returns the GLFW window's size in logical screen points. Dividing the framebuffer extent by it
 * gives the display's pixel scale.
 *
 * @param window The GLFW window handle, as a `Long`.
 * @throws ClassCastException If [window] is not a `Long`.
 */
actual fun windowLogicalExtent(window: Any): VkExtent2D? {
    val handle = window as Long
    return VkExtent2D(
        width = VulkanWindow.glfwGetWindowWidth(handle),
        height = VulkanWindow.glfwGetWindowHeight(handle),
    )
}
