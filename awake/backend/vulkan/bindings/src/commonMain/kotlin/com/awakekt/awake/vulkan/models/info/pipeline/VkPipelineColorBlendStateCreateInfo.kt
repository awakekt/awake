/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkConstArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkBlendFactor
import com.awakekt.awake.vulkan.enums.VkBlendOp
import com.awakekt.awake.vulkan.enums.VkColorComponentFlagBits
import com.awakekt.awake.vulkan.enums.VkColorComponentFlags
import com.awakekt.awake.vulkan.enums.VkLogicOp
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkPipelineColorBlendAttachmentState(
    val blendEnable: VkBool32 = false,
    val srcColorBlendFactor: VkBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE,
    val dstColorBlendFactor: VkBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ZERO,
    val colorBlendOp: VkBlendOp = VkBlendOp.VK_BLEND_OP_ADD,
    val srcAlphaBlendFactor: VkBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE,
    val dstAlphaBlendFactor: VkBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ZERO,
    val alphaBlendOp: VkBlendOp = VkBlendOp.VK_BLEND_OP_ADD,
    val colorWriteMask: VkColorComponentFlags = VkColorComponentFlagBits.VK_COLOR_COMPONENT_R_BIT.value or VkColorComponentFlagBits.VK_COLOR_COMPONENT_G_BIT.value or VkColorComponentFlagBits.VK_COLOR_COMPONENT_B_BIT.value or VkColorComponentFlagBits.VK_COLOR_COMPONENT_A_BIT.value,
)

class VkPipelineColorBlendStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineColorBlendStateCreateFlags = 0,
    val logicOpEnable: VkBool32 = false,
    val logicOp: VkLogicOp = VkLogicOp.VK_LOGIC_OP_COPY,
    @field:VkArray("attachmentCount")
    val pAttachments: Array<VkPipelineColorBlendAttachmentState>? = null,
    @VkConstArray
    val blendConstants: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f),
)

typealias VkPipelineColorBlendStateCreateFlags = VkFlags
