/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.NativeSurfaceWindow
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

data class VkAndroidSurfaceCreateInfoKHR(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR,
    val pNext: Any? = null,
    val flags: VkAndroidSurfaceCreateFlagsKHR = 0,
    @field:NativeSurfaceWindow
    val window: Any? = null,
)

// You can define the VkAndroidSurfaceCreateFlagsKHR as a typealias or enum class
typealias VkAndroidSurfaceCreateFlagsKHR = VkFlags
