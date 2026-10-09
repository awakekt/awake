/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkComponentSwizzle
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlags
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating an image view (`VkImageViewCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of image view creation flags; 0 for none.
 * @property image The raw handle of the image the view is made of.
 * @property viewType The dimensionality the view presents the image as.
 * @property format The format the view interprets the image in.
 * @property components How each component is remapped when read.
 * @property subresourceRange The mip levels and array layers the view covers.
 */
data class VkImageViewCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
    val pNext: Any? = null,
    val flags: Int = 0,
    @field:VkHandleRef("VkImage")
    val image: VkHandle = 0,
    val viewType: VkImageViewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
    val format: VkFormat = VkFormat.VK_FORMAT_UNDEFINED,
    val components: VkComponentMapping = VkComponentMapping(),
    val subresourceRange: VkImageSubresourceRange = VkImageSubresourceRange(),
)

/**
 * How an image view remaps its four components (`VkComponentMapping`).
 *
 * @property r Where the red component reads from.
 * @property g Where the green component reads from.
 * @property b Where the blue component reads from.
 * @property a Where the alpha component reads from.
 */
data class VkComponentMapping(
    val r: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
    val g: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
    val b: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
    val a: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
)

/**
 * A range of mip levels and array layers of an image (`VkImageSubresourceRange`).
 *
 * @property aspectMask A mask of the image aspects included, such as colour or depth.
 * @property baseMipLevel The first mip level in the range.
 * @property levelCount The number of mip levels in the range.
 * @property baseArrayLayer The first array layer in the range.
 * @property layerCount The number of array layers in the range.
 */
data class VkImageSubresourceRange(
    val aspectMask: VkImageAspectFlags = 0,
    val baseMipLevel: Int = 0,
    val levelCount: Int = 1,
    val baseArrayLayer: Int = 0,
    val layerCount: Int = 1,
)
