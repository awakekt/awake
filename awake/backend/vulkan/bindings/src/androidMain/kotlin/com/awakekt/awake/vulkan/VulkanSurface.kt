/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.info.VkAndroidSurfaceCreateInfoKHR

actual fun createSurface(instance: Long, window: Any): Long {
    val surfaceInfo = VkAndroidSurfaceCreateInfoKHR(window = window)
    return Vulkan.vkCreateAndroidSurfaceKHR(instance, surfaceInfo)
}

actual fun surfaceFramebufferExtent(window: Any): VkExtent2D? = null

// Android's DisplayMetrics.density is a separate follow-up, out of this fix's scope -- null
// keeps density at its unscaled default, the same behavior Android already had.
actual fun windowLogicalExtent(window: Any): VkExtent2D? = null

actual fun destroySurfaceWindow(window: Any) {
    // Android owns its own Surface/window lifecycle -- nothing to tear down here.
}
