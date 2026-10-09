/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

// iOS owns its window/layer via UIKit/CAMetalLayer, not GLFW -- see VulkanWindow.kt's
// doc comment.
/**
 * iOS actual of [VulkanWindow], which exists only for desktop GLFW windows. iOS creates its surface
 * from a `CAMetalLayer` through [com.awakekt.awake.vulkan.createSurface], so every window call here
 * throws [NotImplementedError] and the required-extensions list is empty.
 */
actual object VulkanWindow {
    /**
     * Not applicable on iOS: always throws [NotImplementedError]. Use
     * [com.awakekt.awake.vulkan.surfaceFramebufferExtent].
     */
    actual fun glfwGetFramebufferWidth(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    /**
     * Not applicable on iOS: always throws [NotImplementedError]. Use
     * [com.awakekt.awake.vulkan.surfaceFramebufferExtent].
     */
    actual fun glfwGetFramebufferHeight(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    /** Not applicable on iOS: always throws [NotImplementedError]. */
    actual fun glfwGetWindowWidth(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    /** Not applicable on iOS: always throws [NotImplementedError]. */
    actual fun glfwGetWindowHeight(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    /**
     * Not applicable on iOS: always throws [NotImplementedError]. Use
     * [com.awakekt.awake.vulkan.createSurface].
     */
    actual fun glfwCreateWindowSurface(instance: Long, window: Long): Long {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    /** Returns an empty array: iOS needs no GLFW instance extensions. */
    actual fun glfwGetRequiredInstanceExtensions(): Array<String> = emptyArray()
}
