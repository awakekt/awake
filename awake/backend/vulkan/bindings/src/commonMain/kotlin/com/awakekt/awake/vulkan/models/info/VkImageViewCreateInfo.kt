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

data class VkComponentMapping(
    val r: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
    val g: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
    val b: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
    val a: VkComponentSwizzle = VkComponentSwizzle.VK_COMPONENT_SWIZZLE_IDENTITY,
)

data class VkImageSubresourceRange(
    val aspectMask: VkImageAspectFlags = 0,
    val baseMipLevel: Int = 0,
    val levelCount: Int = 1,
    val baseArrayLayer: Int = 0,
    val layerCount: Int = 1,
)
