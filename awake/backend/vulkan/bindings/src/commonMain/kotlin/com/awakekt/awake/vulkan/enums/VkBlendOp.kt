/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The operation that combines the weighted source and destination values when blending
 * (`VkBlendOp`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkBlendOp(val value: Int) {
    /** The result is source plus destination. */
    VK_BLEND_OP_ADD(0),

    /** The result is source minus destination. */
    VK_BLEND_OP_SUBTRACT(1),

    /** The result is destination minus source. */
    VK_BLEND_OP_REVERSE_SUBTRACT(2),

    /**
     * The result is the smaller of source and destination per component; the blend factors are
     * ignored.
     */
    VK_BLEND_OP_MIN(3),

    /**
     * The result is the larger of source and destination per component; the blend factors are
     * ignored.
     */
    VK_BLEND_OP_MAX(4),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: clears the result to zero.
     */
    VK_BLEND_OP_ZERO_EXT(1000148000),

    /** Advanced blend operation from `VK_EXT_blend_operation_advanced`: uses the source. */
    VK_BLEND_OP_SRC_EXT(1000148001),

    /** Advanced blend operation from `VK_EXT_blend_operation_advanced`: uses the destination. */
    VK_BLEND_OP_DST_EXT(1000148002),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: source composited over the
     * destination (Porter-Duff source over).
     */
    VK_BLEND_OP_SRC_OVER_EXT(1000148003),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: destination composited over
     * the source (Porter-Duff destination over).
     */
    VK_BLEND_OP_DST_OVER_EXT(1000148004),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: source where the destination
     * is present (Porter-Duff source in).
     */
    VK_BLEND_OP_SRC_IN_EXT(1000148005),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: destination where the source
     * is present (Porter-Duff destination in).
     */
    VK_BLEND_OP_DST_IN_EXT(1000148006),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: source where the destination
     * is absent (Porter-Duff source out).
     */
    VK_BLEND_OP_SRC_OUT_EXT(1000148007),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: destination where the source
     * is absent (Porter-Duff destination out).
     */
    VK_BLEND_OP_DST_OUT_EXT(1000148008),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: source over the destination,
     * limited to the destination's coverage (Porter-Duff source atop).
     */
    VK_BLEND_OP_SRC_ATOP_EXT(1000148009),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: destination over the source,
     * limited to the source's coverage (Porter-Duff destination atop).
     */
    VK_BLEND_OP_DST_ATOP_EXT(1000148010),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: source and destination where
     * they do not overlap (Porter-Duff xor).
     */
    VK_BLEND_OP_XOR_EXT(1000148011),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: multiplies source and
     * destination.
     */
    VK_BLEND_OP_MULTIPLY_EXT(1000148012),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: inverts, multiplies and
     * inverts again, which lightens.
     */
    VK_BLEND_OP_SCREEN_EXT(1000148013),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: multiplies or screens
     * depending on the destination.
     */
    VK_BLEND_OP_OVERLAY_EXT(1000148014),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: keeps the darker of source
     * and destination.
     */
    VK_BLEND_OP_DARKEN_EXT(1000148015),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: keeps the lighter of source
     * and destination.
     */
    VK_BLEND_OP_LIGHTEN_EXT(1000148016),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: brightens the destination to
     * reflect the source.
     */
    VK_BLEND_OP_COLORDODGE_EXT(1000148017),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: darkens the destination to
     * reflect the source.
     */
    VK_BLEND_OP_COLORBURN_EXT(1000148018),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: multiplies or screens
     * depending on the source.
     */
    VK_BLEND_OP_HARDLIGHT_EXT(1000148019),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: darkens or lightens
     * depending on the source, with a softer result than hard light.
     */
    VK_BLEND_OP_SOFTLIGHT_EXT(1000148020),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: absolute difference of
     * source and destination.
     */
    VK_BLEND_OP_DIFFERENCE_EXT(1000148021),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: like difference with lower
     * contrast.
     */
    VK_BLEND_OP_EXCLUSION_EXT(1000148022),

    /** Advanced blend operation from `VK_EXT_blend_operation_advanced`: inverts the destination. */
    VK_BLEND_OP_INVERT_EXT(1000148023),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: inverts the destination's
     * colour channels, weighted by the source.
     */
    VK_BLEND_OP_INVERT_RGB_EXT(1000148024),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: adds source and destination.
     */
    VK_BLEND_OP_LINEARDODGE_EXT(1000148025),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: adds source and destination,
     * then subtracts one.
     */
    VK_BLEND_OP_LINEARBURN_EXT(1000148026),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: combines colour burn and
     * colour dodge depending on the source.
     */
    VK_BLEND_OP_VIVIDLIGHT_EXT(1000148027),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: combines linear burn and
     * linear dodge depending on the source.
     */
    VK_BLEND_OP_LINEARLIGHT_EXT(1000148028),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: replaces the darker or
     * lighter colour depending on the source.
     */
    VK_BLEND_OP_PINLIGHT_EXT(1000148029),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: pushes each channel to 0 or
     * 1 by summing source and destination.
     */
    VK_BLEND_OP_HARDMIX_EXT(1000148030),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: takes the source's hue with
     * the destination's saturation and luminosity.
     */
    VK_BLEND_OP_HSL_HUE_EXT(1000148031),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: takes the source's
     * saturation with the destination's hue and luminosity.
     */
    VK_BLEND_OP_HSL_SATURATION_EXT(1000148032),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: takes the source's hue and
     * saturation with the destination's luminosity.
     */
    VK_BLEND_OP_HSL_COLOR_EXT(1000148033),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: takes the source's
     * luminosity with the destination's hue and saturation.
     */
    VK_BLEND_OP_HSL_LUMINOSITY_EXT(1000148034),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: adds source and destination.
     */
    VK_BLEND_OP_PLUS_EXT(1000148035),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: adds source and destination,
     * clamped to the valid range.
     */
    VK_BLEND_OP_PLUS_CLAMPED_EXT(1000148036),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: adds source and destination,
     * clamping colour to the result alpha.
     */
    VK_BLEND_OP_PLUS_CLAMPED_ALPHA_EXT(1000148037),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: adds source and destination,
     * then darkens the result.
     */
    VK_BLEND_OP_PLUS_DARKER_EXT(1000148038),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: subtracts the source from
     * the destination.
     */
    VK_BLEND_OP_MINUS_EXT(1000148039),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: subtracts the source from
     * the destination, clamped to the valid range.
     */
    VK_BLEND_OP_MINUS_CLAMPED_EXT(1000148040),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: adjusts the destination's
     * contrast by the source.
     */
    VK_BLEND_OP_CONTRAST_EXT(1000148041),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: inverts the destination, as
     * OpenVG defines it.
     */
    VK_BLEND_OP_INVERT_OVG_EXT(1000148042),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: writes only the red channel.
     */
    VK_BLEND_OP_RED_EXT(1000148043),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: writes only the green
     * channel.
     */
    VK_BLEND_OP_GREEN_EXT(1000148044),

    /**
     * Advanced blend operation from `VK_EXT_blend_operation_advanced`: writes only the blue
     * channel.
     */
    VK_BLEND_OP_BLUE_EXT(1000148045),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_BLEND_OP_MAX_ENUM(0x7FFFFFFF),
}
