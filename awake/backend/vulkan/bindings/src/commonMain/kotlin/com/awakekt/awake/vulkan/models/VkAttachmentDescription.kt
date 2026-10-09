/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkAttachmentLoadOp
import com.awakekt.awake.vulkan.enums.VkAttachmentStoreOp
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageLayout
import com.awakekt.awake.vulkan.enums.VkSampleCountFlagBits

/**
 * Describes one attachment of a render pass: its format, sample count, load and store behaviour and
 * layouts (`VkAttachmentDescription`).
 *
 * @property flags Attachment flags; 0 for none, or may-alias to let the attachment share memory
 * with another.
 * @property format The format of the image view used for the attachment.
 * @property samples The number of samples of the image.
 * @property loadOp What happens to the colour or depth contents at the start of the first subpass
 * that uses the attachment.
 * @property storeOp What happens to the colour or depth contents at the end of the last subpass
 * that uses the attachment.
 * @property stencilLoadOp What happens to the stencil contents at the start of the first subpass
 * that uses the attachment.
 * @property stencilStoreOp What happens to the stencil contents at the end of the last subpass that
 * uses the attachment.
 * @property initialLayout The layout the image must be in when the render pass begins.
 * @property finalLayout The layout the image is transitioned to when the render pass ends.
 */
data class VkAttachmentDescription(
    val flags: VkAttachmentDescriptionFlags = 0,
    val format: VkFormat = VkFormat.VK_FORMAT_UNDEFINED,
    val samples: VkSampleCountFlagBits = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT,
    val loadOp: VkAttachmentLoadOp = VkAttachmentLoadOp.CLEAR,
    val storeOp: VkAttachmentStoreOp = VkAttachmentStoreOp.STORE,
    val stencilLoadOp: VkAttachmentLoadOp = VkAttachmentLoadOp.DONT_CARE,
    val stencilStoreOp: VkAttachmentStoreOp = VkAttachmentStoreOp.DONT_CARE,
    val initialLayout: VkImageLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
    val finalLayout: VkImageLayout = VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
)

typealias VkAttachmentDescriptionFlags = VkFlags
