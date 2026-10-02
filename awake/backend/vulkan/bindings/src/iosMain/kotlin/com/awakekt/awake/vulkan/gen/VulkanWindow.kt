/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

// iOS owns its window/layer via UIKit/CAMetalLayer, not GLFW -- see VulkanWindow.kt's
// doc comment.
actual object VulkanWindow {
    actual fun glfwGetFramebufferWidth(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetFramebufferHeight(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetWindowWidth(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetWindowHeight(window: Long): Int {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwCreateWindowSurface(instance: Long, window: Long): Long {
        TODO("Not applicable on iOS -- see VulkanWindow.kt's doc comment.")
    }

    actual fun glfwGetRequiredInstanceExtensions(): Array<String> = emptyArray()
}
