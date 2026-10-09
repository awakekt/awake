/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkColorSpaceKHR
import com.awakekt.awake.vulkan.enums.VkCompositeAlphaFlagBitsKHR
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageUsageFlags
import com.awakekt.awake.vulkan.enums.VkPresentModeKHR
import com.awakekt.awake.vulkan.enums.VkSharingMode
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR
import com.awakekt.awake.vulkan.models.VkExtent2D

/**
 * Parameters for creating a swapchain (`VkSwapchainCreateInfoKHR`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of swapchain creation flags; 0 for none.
 * @property surface The raw handle of the surface the swapchain presents to.
 * @property minImageCount The smallest number of images the application is willing to use.
 * @property imageFormat The format of the swapchain images.
 * @property imageColorSpace The colour space the images are interpreted in.
 * @property imageExtent The size of the swapchain images, in pixels.
 * @property imageArrayLayers The number of layers per image; 1 unless rendering stereoscopically.
 * @property imageUsage A mask of the usages the images are created for.
 * @property imageSharingMode Whether the images belong to one queue family at a time or are shared
 * between several.
 * @property pQueueFamilyIndices The queue families that share the images; `null` for exclusive
 * sharing.
 * @property preTransform The transform applied to the images before presentation.
 * @property compositeAlpha How the alpha channel is handled when compositing with other windows.
 * @property presentMode How finished images are queued and shown.
 * @property clipped Whether pixels hidden by other windows may be discarded.
 * @property oldSwapchain The raw handle of the swapchain being replaced, or 0 for none.
 */
class VkSwapchainCreateInfoKHR(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR,
    val pNext: Any? = null, // You can use the appropriate type for pNext based on your requirements
    val flags: Int = 0,
    @field:VkHandleRef("VkSurfaceKHR")
    val surface: VkHandle = 0, // VkSurfaceKHR,
    val minImageCount: Int = 0,
    val imageFormat: VkFormat = VkFormat.VK_FORMAT_UNDEFINED,
    val imageColorSpace: VkColorSpaceKHR = VkColorSpaceKHR.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR,
    val imageExtent: VkExtent2D = VkExtent2D(),
    val imageArrayLayers: Int = 0,
    val imageUsage: VkImageUsageFlags = 0,
    val imageSharingMode: VkSharingMode = VkSharingMode.VK_SHARING_MODE_EXCLUSIVE,
    @field:VkArray("queueFamilyIndexCount")
    val pQueueFamilyIndices: IntArray? = null, // Set it to null if it's optional
    val preTransform: VkSurfaceTransformFlagBitsKHR = VkSurfaceTransformFlagBitsKHR.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR,
    val compositeAlpha: VkCompositeAlphaFlagBitsKHR = VkCompositeAlphaFlagBitsKHR.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
    val presentMode: VkPresentModeKHR = VkPresentModeKHR.VK_PRESENT_MODE_IMMEDIATE_KHR,
    val clipped: VkBool32 = false,
    @field:VkHandleRef("VkSwapchainKHR")
    val oldSwapchain: VkHandle = 0, // VkSwapchainKHR
)
