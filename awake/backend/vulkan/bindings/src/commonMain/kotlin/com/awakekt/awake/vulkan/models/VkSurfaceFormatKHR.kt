/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkColorSpaceKHR
import com.awakekt.awake.vulkan.enums.VkFormat
import kotlin.jvm.JvmOverloads

@VkMutator
data class VkSurfaceFormatKHR @JvmOverloads constructor(
    val format: VkFormat = VkFormat.VK_FORMAT_UNDEFINED,
    val colorSpace: VkColorSpaceKHR = VkColorSpaceKHR.VK_COLORSPACE_SRGB_NONLINEAR_KHR,
)
