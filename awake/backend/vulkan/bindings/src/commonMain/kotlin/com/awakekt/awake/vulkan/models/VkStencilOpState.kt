/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.enums.VkCompareOp
import com.awakekt.awake.vulkan.enums.VkStencilOp

/**
 * The stencil test and update operations for front-facing or back-facing triangles
 * (`VkStencilOpState`).
 *
 * @property failOp The action performed on samples that fail the stencil test.
 * @property passOp The action performed on samples that pass both the depth and stencil tests.
 * @property depthFailOp The action performed on samples that pass the stencil test and fail the
 * depth test.
 * @property compareOp The comparison used by the stencil test.
 * @property compareMask The bits of the stencil value and the reference that take part in the
 * comparison.
 * @property writeMask The bits of the stencil value that are updated.
 * @property reference The reference value used by the comparison and by the replace operation.
 */
class VkStencilOpState(
    var failOp: VkStencilOp = VkStencilOp.VK_STENCIL_OP_KEEP,
    var passOp: VkStencilOp = VkStencilOp.VK_STENCIL_OP_KEEP,
    var depthFailOp: VkStencilOp = VkStencilOp.VK_STENCIL_OP_KEEP,
    var compareOp: VkCompareOp = VkCompareOp.VK_COMPARE_OP_ALWAYS,
    var compareMask: Int = 0,
    var writeMask: Int = 0,
    var reference: Int = 0,
)
