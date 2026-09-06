/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkCompareOp
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.models.VkStencilOpState

class VkPipelineDepthStencilStateCreateInfo(
    var sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO,
    var pNext: Any? = null,
    var flags: VkPipelineDepthStencilStateCreateFlags = 0,
    var depthTestEnable: VkBool32 = true,
    var depthWriteEnable: VkBool32 = true,
    var depthCompareOp: VkCompareOp = VkCompareOp.VK_COMPARE_OP_LESS_OR_EQUAL,
    var depthBoundsTestEnable: VkBool32 = false,
    var stencilTestEnable: VkBool32 = false,
    var front: VkStencilOpState = VkStencilOpState(),
    var back: VkStencilOpState = VkStencilOpState(),
    var minDepthBounds: Float = 0.0f,
    var maxDepthBounds: Float = 1.0f,
)

typealias VkPipelineDepthStencilStateCreateFlags = VkFlags
