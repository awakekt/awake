/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport

/**
 * The viewports and scissors of a graphics pipeline (`VkPipelineViewportStateCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property pViewports The viewports; `null` when the viewport is dynamic.
 * @property pScissors The scissor rectangles; `null` when the scissor is dynamic.
 */
class VkPipelineViewportStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineViewportStateCreateFlags = 0,
    @VkArray(sizeAlias = "viewportCount")
    val pViewports: Array<VkViewport>? = null,
    @VkArray(sizeAlias = "scissorCount")
    val pScissors: Array<VkRect2D>? = null,
)

typealias VkPipelineViewportStateCreateFlags = VkFlags
