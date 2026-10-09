/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.models.VkAttachmentDescription
import com.awakekt.awake.vulkan.models.VkSubpassDependency

/**
 * Parameters for creating a render pass (`VkRenderPassCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Render pass creation flags; 0 for none.
 * @property pAttachments The attachments used by the render pass.
 * @property pSubpasses The subpasses of the render pass.
 * @property pDependencies The dependencies between subpasses.
 */
class VkRenderPassCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkRenderPassCreateFlags = 0,
    @VkArray(sizeAlias = "attachmentCount")
    val pAttachments: Array<VkAttachmentDescription>? = null,
    @VkArray(sizeAlias = "subpassCount")
    val pSubpasses: Array<VkSubpassDescription>? = null,
    @VkArray(sizeAlias = "dependencyCount")
    val pDependencies: Array<VkSubpassDependency>? = null,
)

typealias VkRenderPassCreateFlags = VkFlags
