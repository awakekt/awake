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

/**
 * The blend state for one colour attachment (`VkPipelineColorBlendAttachmentState`).
 *
 * @property blendEnable Whether blending is on for the attachment.
 * @property srcColorBlendFactor The factor applied to the source colour.
 * @property dstColorBlendFactor The factor applied to the destination colour.
 * @property colorBlendOp The operation that combines the weighted source and destination colour.
 * @property srcAlphaBlendFactor The factor applied to the source alpha.
 * @property dstAlphaBlendFactor The factor applied to the destination alpha.
 * @property alphaBlendOp The operation that combines the weighted source and destination alpha.
 * @property colorWriteMask A mask of the colour channels that are written; all four by default.
 */
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

/**
 * The colour blend state of a graphics pipeline (`VkPipelineColorBlendStateCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of creation flags; 0 for none.
 * @property logicOpEnable Whether a logical operation replaces blending.
 * @property logicOp The logical operation, when enabled.
 * @property pAttachments The blend state of each colour attachment.
 * @property blendConstants The four constant blend colour components: red, green, blue and alpha.
 */
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
