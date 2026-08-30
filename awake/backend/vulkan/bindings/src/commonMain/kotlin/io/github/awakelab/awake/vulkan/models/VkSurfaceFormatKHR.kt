/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models

import io.github.awakelab.awake.vulkan.VkMutator
import io.github.awakelab.awake.vulkan.enums.VkColorSpaceKHR
import io.github.awakelab.awake.vulkan.enums.VkFormat
import kotlin.jvm.JvmOverloads

@VkMutator
data class VkSurfaceFormatKHR @JvmOverloads constructor(
    val format: VkFormat = VkFormat.VK_FORMAT_UNDEFINED,
    val colorSpace: VkColorSpaceKHR = VkColorSpaceKHR.VK_COLORSPACE_SRGB_NONLINEAR_KHR,
)
