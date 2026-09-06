/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.enums.VkCompareOp
import com.awakekt.awake.vulkan.enums.VkStencilOp

class VkStencilOpState(
    var failOp: VkStencilOp = VkStencilOp.VK_STENCIL_OP_KEEP,
    var passOp: VkStencilOp = VkStencilOp.VK_STENCIL_OP_KEEP,
    var depthFailOp: VkStencilOp = VkStencilOp.VK_STENCIL_OP_KEEP,
    var compareOp: VkCompareOp = VkCompareOp.VK_COMPARE_OP_ALWAYS,
    var compareMask: Int = 0,
    var writeMask: Int = 0,
    var reference: Int = 0,
)
