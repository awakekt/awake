/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.VulkanNativeLoader

/**
 * Desktop JVM actual of [VulkanWindow]: JNI `external` declarations over GLFW and the Vulkan loader
 * in the `awake-vulkan` library. Unlike the other generated objects it loads the library itself,
 * through its own initialiser, so it can be used before [com.awakekt.awake.vulkan.Vulkan].
 */
actual object VulkanWindow {
    init {
        VulkanNativeLoader.load()
    }

    /**
     * Returns the window's framebuffer width in device pixels, which is what a swapchain extent is
     * built from.
     */
    actual external fun glfwGetFramebufferWidth(window: Long): Int

    /**
     * Returns the window's framebuffer height in device pixels, which is what a swapchain extent is
     * built from.
     */
    actual external fun glfwGetFramebufferHeight(window: Long): Int

    /** Size in logical points; the framebuffer is a device-pixel multiple of it on HiDPI. */
    actual external fun glfwGetWindowWidth(window: Long): Int

    /**
     * Returns the window's height in logical points; the framebuffer height is a device-pixel
     * multiple of it on HiDPI displays.
     */
    actual external fun glfwGetWindowHeight(window: Long): Int

    /**
     * A `VkSurfaceKHR` for [window] on [instance]; the desktop counterpart of
     * `vkCreateAndroidSurfaceKHR`.
     */
    actual external fun glfwCreateWindowSurface(instance: Long, window: Long): Long

    /**
     * Instance extensions a window surface needs (`VK_KHR_surface` plus the platform's own). Pass
     * them to `VkInstanceCreateInfo` before [glfwCreateWindowSurface], or it fails with
     * `VK_ERROR_INITIALIZATION_FAILED`.
     */
    actual external fun glfwGetRequiredInstanceExtensions(): Array<String>
}
