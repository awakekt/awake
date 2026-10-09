/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating a framebuffer (`VkFramebufferCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Framebuffer creation flags; 0 for none.
 * @property renderPass The raw handle of the render pass the framebuffer is compatible with.
 * @property pAttachments The image views bound to the render pass's attachments, in the same order.
 * @property width The framebuffer width, in pixels.
 * @property height The framebuffer height, in pixels.
 * @property layers The number of layers of the framebuffer.
 */
class VkFramebufferCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkFramebufferCreateFlags = 0,
    @field:VkHandleRef("VkRenderPass")
    val renderPass: VkHandle = 0,
    @field:VkHandleRef("VkImageView")
    @field:VkArray(sizeAlias = "attachmentCount")
    val pAttachments: Array<VkImageView> = emptyArray(),
    val width: Int = 0,
    val height: Int = 0,
    val layers: Int = 0,
)

typealias VkImageView = VkHandle
typealias VkFramebufferCreateFlags = VkFlags
