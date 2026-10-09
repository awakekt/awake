/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.flags.VkQueryControlFlags
import com.awakekt.awake.vulkan.enums.flags.VkQueryPipelineStatisticFlags

/**
 * The state a secondary command buffer inherits from the primary buffer that executes it
 * (`VkCommandBufferInheritanceInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property renderPass The raw handle of the render pass the buffer will be executed inside, if
 * any.
 * @property subpass The index of the subpass the buffer will be executed in.
 * @property framebuffer The raw handle of the framebuffer the buffer will render to, or 0 if
 * unknown.
 * @property occlusionQueryEnable Whether the primary command buffer may have an occlusion query
 * active.
 * @property queryFlags A mask of the query control flags that may be used by an active occlusion
 * query.
 * @property pipelineStatistics A mask of the pipeline statistics that may be counted by active
 * queries.
 */
data class VkCommandBufferInheritanceInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO,
    val pNext: Any? = null,
    @field:VkHandleRef("VkRenderPass")
    val renderPass: VkHandle = 0,
    val subpass: UInt = 0u,
    @field:VkHandleRef("VkFramebuffer")
    val framebuffer: VkHandle = 0,
    val occlusionQueryEnable: VkBool32 = false,
    val queryFlags: VkQueryControlFlags = 0,
    val pipelineStatistics: VkQueryPipelineStatisticFlags = 0,
)
