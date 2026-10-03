/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

// Android owns its window via android.view.Surface/SurfaceView, not GLFW -- see
// Vulkan.vkCreateAndroidSurfaceKHR. This object is desktop-only; see VulkanWindow.kt's
// doc comment.
actual object VulkanWindow {
    actual fun glfwGetFramebufferWidth(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetFramebufferHeight(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetWindowWidth(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetWindowHeight(window: Long): Int {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwCreateWindowSurface(instance: Long, window: Long): Long {
        TODO("Not applicable on Android -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetRequiredInstanceExtensions(): Array<String> = emptyArray()
}
