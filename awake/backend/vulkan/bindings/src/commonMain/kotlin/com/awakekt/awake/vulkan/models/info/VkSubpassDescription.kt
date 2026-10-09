/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.models.VkAttachmentReference

/**
 * Describes one subpass of a render pass: the attachments it reads and writes
 * (`VkSubpassDescription`).
 *
 * @property flags A mask of subpass description flags; 0 for none.
 * @property pipelineBindPoint Whether the subpass runs graphics or compute pipelines.
 * @property pInputAttachments The attachments read as input attachments.
 * @property pColorAttachments The attachments written as colour attachments.
 * @property pResolveAttachments The attachments multisampled colour attachments are resolved into,
 * or `null` for none.
 * @property pDepthStencilAttachment The depth/stencil attachment, or `null` for none.
 * @property pPreserveAttachments The indices of attachments the subpass does not use but whose
 * contents it must preserve.
 */
class VkSubpassDescription(
    val flags: VkSubpassDescriptionFlags = 0,
    val pipelineBindPoint: VkPipelineBindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
    @field:VkArray(sizeAlias = "inputAttachmentCount")
    val pInputAttachments: Array<VkAttachmentReference>? = null,
    @field:VkArray(sizeAlias = "colorAttachmentCount")
    val pColorAttachments: Array<VkAttachmentReference>? = null,
    val pResolveAttachments: Array<VkAttachmentReference>? = null,
    val pDepthStencilAttachment: Array<VkAttachmentReference>? = null,
    @field:VkArray(sizeAlias = "preserveAttachmentCount")
    val pPreserveAttachments: IntArray? = null,
)

typealias VkSubpassDescriptionFlags = VkFlags
