/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * A factor applied to the source or destination colour or alpha when blending (`VkBlendFactor`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkBlendFactor(val value: Int) {
    /** The factor is (0, 0, 0, 0). */
    VK_BLEND_FACTOR_ZERO(0),

    /** The factor is (1, 1, 1, 1). */
    VK_BLEND_FACTOR_ONE(1),

    /** The factor is the fragment shader's output colour. */
    VK_BLEND_FACTOR_SRC_COLOR(2),

    /** The factor is one minus the fragment shader's output colour. */
    VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR(3),

    /** The factor is the colour already in the attachment. */
    VK_BLEND_FACTOR_DST_COLOR(4),

    /** The factor is one minus the colour already in the attachment. */
    VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR(5),

    /** The factor is the fragment shader's output alpha, in every channel. */
    VK_BLEND_FACTOR_SRC_ALPHA(6),

    /** The factor is one minus the fragment shader's output alpha, in every channel. */
    VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA(7),

    /** The factor is the alpha already in the attachment, in every channel. */
    VK_BLEND_FACTOR_DST_ALPHA(8),

    /** The factor is one minus the alpha already in the attachment, in every channel. */
    VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA(9),

    /** The factor is the pipeline's constant blend colour. */
    VK_BLEND_FACTOR_CONSTANT_COLOR(10),

    /** The factor is one minus the pipeline's constant blend colour. */
    VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR(11),

    /** The factor is the alpha of the pipeline's constant blend colour, in every channel. */
    VK_BLEND_FACTOR_CONSTANT_ALPHA(12),

    /** The factor is one minus the alpha of the constant blend colour, in every channel. */
    VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA(13),

    /** The colour factor is min(source alpha, 1 - destination alpha) and the alpha factor is 1. */
    VK_BLEND_FACTOR_SRC_ALPHA_SATURATE(14),

    /** The factor is the fragment shader's second output colour, for dual-source blending. */
    VK_BLEND_FACTOR_SRC1_COLOR(15),

    /** The factor is one minus the fragment shader's second output colour. */
    VK_BLEND_FACTOR_ONE_MINUS_SRC1_COLOR(16),

    /** The factor is the alpha of the fragment shader's second output, in every channel. */
    VK_BLEND_FACTOR_SRC1_ALPHA(17),

    /** The factor is one minus the alpha of the second output, in every channel. */
    VK_BLEND_FACTOR_ONE_MINUS_SRC1_ALPHA(18),
}
