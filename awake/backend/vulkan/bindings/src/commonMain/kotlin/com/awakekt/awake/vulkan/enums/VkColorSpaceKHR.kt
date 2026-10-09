/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How a swapchain image's colour values are interpreted by the presentation engine
 * (`VkColorSpaceKHR`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkColorSpaceKHR(val value: Int) {
    /**
     * sRGB primaries with the sRGB transfer function, the colour space of ordinary SDR displays.
     */
    VK_COLOR_SPACE_SRGB_NONLINEAR_KHR(0),

    /** Display-P3 primaries with the sRGB transfer function. */
    VK_COLOR_SPACE_DISPLAY_P3_NONLINEAR_EXT(1000104001),

    /** scRGB: sRGB primaries with a linear transfer function and values beyond 0 to 1. */
    VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT(1000104002),

    /** Display-P3 primaries with a linear transfer function. */
    VK_COLOR_SPACE_DISPLAY_P3_LINEAR_EXT(1000104003),

    /** DCI-P3 primaries with the DCI-P3 transfer function. */
    VK_COLOR_SPACE_DCI_P3_NONLINEAR_EXT(1000104004),

    /** BT.709 primaries with a linear transfer function. */
    VK_COLOR_SPACE_BT709_LINEAR_EXT(1000104005),

    /** BT.709 primaries with the BT.709 transfer function. */
    VK_COLOR_SPACE_BT709_NONLINEAR_EXT(1000104006),

    /** BT.2020 primaries with a linear transfer function. */
    VK_COLOR_SPACE_BT2020_LINEAR_EXT(1000104007),

    /** HDR10: BT.2020 primaries with the SMPTE ST 2084 perceptual quantizer transfer function. */
    VK_COLOR_SPACE_HDR10_ST2084_EXT(1000104008),

    /** Dolby Vision: BT.2020 primaries with the perceptual quantizer transfer function. */
    VK_COLOR_SPACE_DOLBYVISION_EXT(1000104009),

    /** HDR10 HLG: BT.2020 primaries with the hybrid log-gamma transfer function. */
    VK_COLOR_SPACE_HDR10_HLG_EXT(1000104010),

    /** AdobeRGB primaries with a linear transfer function. */
    VK_COLOR_SPACE_ADOBERGB_LINEAR_EXT(1000104011),

    /** AdobeRGB primaries with the AdobeRGB transfer function. */
    VK_COLOR_SPACE_ADOBERGB_NONLINEAR_EXT(1000104012),

    /**
     * Colour values are passed to the display unchanged, leaving colour management to the
     * application.
     */
    VK_COLOR_SPACE_PASS_THROUGH_EXT(1000104013),

    /** Extended-range sRGB primaries with the sRGB transfer function. */
    VK_COLOR_SPACE_EXTENDED_SRGB_NONLINEAR_EXT(1000104014),

    /** The display's native colour space, as exposed by AMD's FreeSync 2. */
    VK_COLOR_SPACE_DISPLAY_NATIVE_AMD(1000213000),

    /** Alias of [VK_COLOR_SPACE_SRGB_NONLINEAR_KHR]; both names carry the same value. */
    VK_COLORSPACE_SRGB_NONLINEAR_KHR(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR.value),

    /** Alias of [VK_COLOR_SPACE_DISPLAY_P3_LINEAR_EXT]; both names carry the same value. */
    VK_COLOR_SPACE_DCI_P3_LINEAR_EXT(VK_COLOR_SPACE_DISPLAY_P3_LINEAR_EXT.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_COLOR_SPACE_MAX_ENUM_KHR(0x7FFFFFFF),
}
