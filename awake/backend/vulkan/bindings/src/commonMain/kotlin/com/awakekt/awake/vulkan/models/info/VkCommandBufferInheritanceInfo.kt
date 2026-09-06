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
