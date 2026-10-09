/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.enums.VkImageLayout

/**
 * A subpass's reference to a render pass attachment and the layout it is in during that subpass
 * (`VkAttachmentReference`).
 *
 * @property attachment Index into the render pass's attachment array, or `VK_ATTACHMENT_UNUSED` for
 * an unused slot.
 * @property layout The layout the attachment is in during the subpass.
 */
data class VkAttachmentReference(
    val attachment: Int = 0,
    val layout: VkImageLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
)
