/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

/**
 * The Vulkan side of a desktop GLFW window: its surface, the instance extensions that surface
 * needs, and the sizes a swapchain is built from.
 *
 * The window itself, its input and its lifetime belong to `awake:engine:window`, which hands this
 * backend the window handle. Desktop only: Android and iOS create their surface from a platform
 * `Surface` or `CAMetalLayer` (see `VulkanSurface.kt`), so the other actuals are stubs.
 */
expect object VulkanWindow {
    /**
     * Returns the window's framebuffer width in device pixels, which is what a swapchain extent is
     * built from.
     *
     * @param window The GLFW window handle.
     */
    fun glfwGetFramebufferWidth(window: Long): Int

    /**
     * Returns the window's framebuffer height in device pixels, which is what a swapchain extent is
     * built from.
     *
     * @param window The GLFW window handle.
     */
    fun glfwGetFramebufferHeight(window: Long): Int

    /** Size in logical points; the framebuffer is a device-pixel multiple of it on HiDPI. */
    fun glfwGetWindowWidth(window: Long): Int

    /**
     * Returns the window's height in logical points; the framebuffer height is a device-pixel
     * multiple of it on HiDPI displays.
     *
     * @param window The GLFW window handle.
     */
    fun glfwGetWindowHeight(window: Long): Int

    /** A `VkSurfaceKHR` for [window] on [instance]; the desktop counterpart of `vkCreateAndroidSurfaceKHR`. */
    fun glfwCreateWindowSurface(instance: Long, window: Long): Long

    /**
     * Instance extensions a window surface needs (`VK_KHR_surface` plus the platform's own). Pass
     * them to `VkInstanceCreateInfo` before [glfwCreateWindowSurface], or it fails with
     * `VK_ERROR_INITIALIZATION_FAILED`.
     */
    fun glfwGetRequiredInstanceExtensions(): Array<String>
}
