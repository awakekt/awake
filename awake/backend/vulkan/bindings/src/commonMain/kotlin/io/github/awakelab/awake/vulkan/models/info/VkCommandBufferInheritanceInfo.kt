/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models.info

import io.github.awakelab.awake.vulkan.VkBool32
import io.github.awakelab.awake.vulkan.VkHandle
import io.github.awakelab.awake.vulkan.VkHandleRef
import io.github.awakelab.awake.vulkan.enums.VkStructureType
import io.github.awakelab.awake.vulkan.enums.flags.VkQueryControlFlags
import io.github.awakelab.awake.vulkan.enums.flags.VkQueryPipelineStatisticFlags

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
