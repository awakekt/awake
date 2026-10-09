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

/**
 * The depth and stencil state of a graphics pipeline (`VkPipelineDepthStencilStateCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of creation flags; 0 for none.
 * @property depthTestEnable Whether the depth test is on.
 * @property depthWriteEnable Whether passing fragments write their depth.
 * @property depthCompareOp The comparison used by the depth test.
 * @property depthBoundsTestEnable Whether the depth bounds test is on.
 * @property stencilTestEnable Whether the stencil test is on.
 * @property front The stencil state for front-facing triangles.
 * @property back The stencil state for back-facing triangles.
 * @property minDepthBounds The lower bound of the depth bounds test.
 * @property maxDepthBounds The upper bound of the depth bounds test.
 */
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
