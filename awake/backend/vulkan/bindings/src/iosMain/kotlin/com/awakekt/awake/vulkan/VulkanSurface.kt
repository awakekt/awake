/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.models.VkExtent2D
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.objcPtr
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.MoltenVK.VK_STRUCTURE_TYPE_METAL_SURFACE_CREATE_INFO_EXT
import platform.MoltenVK.VK_SUCCESS
import platform.MoltenVK.VkMetalSurfaceCreateInfoEXT
import platform.MoltenVK.VkSurfaceKHRVar
import platform.MoltenVK.vkCreateMetalSurfaceEXT
import platform.QuartzCore.CAMetalLayer

// iOS surface creation is CAMetalLayer-backed via MoltenVK's VK_EXT_metal_surface. `window` must
// be a real CAMetalLayer; pLayer is an opaque `const void*` in the cinterop-parsed C header, so
// the ObjC layer is passed via .objcPtr() rather than a generated wrapper type.
/**
 * Creates the `VkSurfaceKHR` for a `CAMetalLayer` through MoltenVK's `VK_EXT_metal_surface`.
 *
 * @param instance The Vulkan instance to create the surface on.
 * @param window The `CAMetalLayer` to present to.
 * @return The new `VkSurfaceKHR` handle.
 * @throws ClassCastException If [window] is not a `CAMetalLayer`.
 * @throws IllegalStateException If surface creation fails.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createSurface(instance: Long, window: Any): Long = memScoped {
    val metalLayer = window as CAMetalLayer
    val nativeCreateInfo = alloc<VkMetalSurfaceCreateInfoEXT>().apply {
        sType = VK_STRUCTURE_TYPE_METAL_SURFACE_CREATE_INFO_EXT
        pNext = null
        flags = 0u
        pLayer = interpretCPointer<CPointed>(metalLayer.objcPtr())
    }
    val surfaceVar = alloc<VkSurfaceKHRVar>()
    val result = vkCreateMetalSurfaceEXT(instance.toCPointer(), nativeCreateInfo.ptr, null, surfaceVar.ptr)
    check(result == VK_SUCCESS) { "vkCreateMetalSurfaceEXT failed: $result" }
    surfaceVar.value!!.rawValue.toLong()
}

/**
 * Returns the layer's drawable size in device pixels, which is what a variable-extent swapchain is
 * built from.
 *
 * @param window The `CAMetalLayer`.
 * @throws ClassCastException If [window] is not a `CAMetalLayer`.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun surfaceFramebufferExtent(window: Any): VkExtent2D? {
    val metalLayer = window as CAMetalLayer
    return metalLayer.drawableSize.useContents {
        VkExtent2D(width = width.toInt(), height = height.toInt())
    }
}

/**
 * Returns the layer's bounds in logical points, or `null` when [window] is not a `CAMetalLayer`.
 * Dividing the drawable size by it gives the display's pixel scale.
 *
 * @param window The `CAMetalLayer`.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun windowLogicalExtent(window: Any): VkExtent2D? {
    val metalLayer = window as? CAMetalLayer ?: return null
    return metalLayer.bounds.useContents {
        VkExtent2D(width = size.width.toInt(), height = size.height.toInt())
    }
}
