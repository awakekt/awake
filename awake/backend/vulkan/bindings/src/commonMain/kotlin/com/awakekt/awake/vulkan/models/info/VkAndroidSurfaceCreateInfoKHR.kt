/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.NativeSurfaceWindow
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating a surface from an Android native window
 * (`VkAndroidSurfaceCreateInfoKHR`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property window The Android `Surface` to present to.
 */
data class VkAndroidSurfaceCreateInfoKHR(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
    val pNext: Any? = null,
    val flags: VkAndroidSurfaceCreateFlagsKHR = 0,
    @field:NativeSurfaceWindow
    val window: Any? = null,
)

// You can define the VkAndroidSurfaceCreateFlagsKHR as a typealias or enum class
typealias VkAndroidSurfaceCreateFlagsKHR = VkFlags
