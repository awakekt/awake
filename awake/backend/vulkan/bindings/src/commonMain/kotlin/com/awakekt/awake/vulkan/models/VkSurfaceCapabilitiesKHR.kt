/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkCompositeAlphaFlagsKHR
import com.awakekt.awake.vulkan.enums.VkImageUsageFlags
import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR
import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagsKHR
import kotlin.jvm.JvmOverloads

/**
 * What a surface supports for swapchains on a physical device (`VkSurfaceCapabilitiesKHR`).
 *
 * @property minImageCount The smallest number of images a swapchain for the surface can have.
 * @property maxImageCount The largest number of images a swapchain can have, or 0 for no limit.
 * @property currentExtent The surface's current size, or a width of `Int.MAX_VALUE` when the size
 * is decided by the swapchain.
 * @property minImageExtent The smallest swapchain image size the surface supports.
 * @property maxImageExtent The largest swapchain image size the surface supports.
 * @property maxImageArrayLayers The largest number of layers a swapchain image can have.
 * @property supportedTransforms A mask of the surface transforms the presentation engine can apply.
 * @property currentTransform The transform the surface is currently using.
 * @property supportedCompositeAlpha A mask of the composite alpha modes the surface supports.
 * @property supportedUsageFlags A mask of the image usages a swapchain image can have.
 */
@VkMutator
data class VkSurfaceCapabilitiesKHR @JvmOverloads constructor(
    val minImageCount: Int = 0,
    val maxImageCount: Int = 0,
    val currentExtent: VkExtent2D = VkExtent2D(),
    val minImageExtent: VkExtent2D = VkExtent2D(),
    val maxImageExtent: VkExtent2D = VkExtent2D(),
    val maxImageArrayLayers: Int = 0,
    val supportedTransforms: VkSurfaceTransformFlagsKHR = 0,
    val currentTransform: VkSurfaceTransformFlagBitsKHR = VkSurfaceTransformFlagBitsKHR.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR,
    val supportedCompositeAlpha: VkCompositeAlphaFlagsKHR = 0,
    val supportedUsageFlags: VkImageUsageFlags = 0,
)
