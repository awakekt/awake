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
