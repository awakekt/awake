/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

// Android owns its window via android.view.Surface/SurfaceView, not GLFW -- see
// Vulkan.vkCreateAndroidSurfaceKHR. This object is desktop-only; see VulkanWindow.kt's
// doc comment.
/**
 * Android actual of [VulkanWindow], which exists only for desktop GLFW windows. Android creates its
 * surface from an `android.view.Surface` through
 * [com.awakekt.awake.vulkan.Vulkan.vkCreateAndroidSurfaceKHR], so every window call here throws
 * [NotImplementedError] and the required-extensions list is empty.
 */
actual object VulkanWindow {
    /**
     * Not applicable on Android: always throws [NotImplementedError]. Use
     * [com.awakekt.awake.vulkan.surfaceFramebufferExtent].
     */
    actual fun glfwGetFramebufferWidth(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    /**
     * Not applicable on Android: always throws [NotImplementedError]. Use
     * [com.awakekt.awake.vulkan.surfaceFramebufferExtent].
     */
    actual fun glfwGetFramebufferHeight(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    /** Not applicable on Android: always throws [NotImplementedError]. */
    actual fun glfwGetWindowWidth(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    /** Not applicable on Android: always throws [NotImplementedError]. */
    actual fun glfwGetWindowHeight(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    /**
     * Not applicable on Android: always throws [NotImplementedError]. Use
     * [com.awakekt.awake.vulkan.createSurface].
     */
    actual fun glfwCreateWindowSurface(instance: Long, window: Long): Long {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    /** Returns an empty array: Android needs no GLFW instance extensions. */
    actual fun glfwGetRequiredInstanceExtensions(): Array<String> = emptyArray()
}
