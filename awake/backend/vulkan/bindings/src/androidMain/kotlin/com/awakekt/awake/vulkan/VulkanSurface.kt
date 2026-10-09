/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.info.VkAndroidSurfaceCreateInfoKHR

/**
 * Creates the `VkSurfaceKHR` for an `android.view.Surface` through
 * [Vulkan.vkCreateAndroidSurfaceKHR].
 *
 * @param instance The Vulkan instance to create the surface on.
 * @param window The `android.view.Surface` to present to.
 * @return The new `VkSurfaceKHR` handle.
 */
actual fun createSurface(instance: Long, window: Any): Long {
    val surfaceInfo = VkAndroidSurfaceCreateInfoKHR(window = window)
    return Vulkan.vkCreateAndroidSurfaceKHR(instance, surfaceInfo)
}

/**
 * Returns `null`: on Android the surface capabilities already carry the extent, so no separate size
 * query is needed.
 *
 * @param window The `android.view.Surface`; unused.
 */
actual fun surfaceFramebufferExtent(window: Any): VkExtent2D? = null

// Android's DisplayMetrics.density is a separate follow-up, out of this fix's scope -- null
// keeps density at its unscaled default, the same behavior Android already had.
/**
 * Returns `null`: Android reports only physical pixels, so the display scale stays at its unscaled
 * default.
 *
 * @param window The `android.view.Surface`; unused.
 */
actual fun windowLogicalExtent(window: Any): VkExtent2D? = null
