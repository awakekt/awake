/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * How the presentation engine treats a swapchain image's alpha channel
 * (`VkCompositeAlphaFlagBitsKHR`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkCompositeAlphaFlagBitsKHR(val value: Int) {
    /** The alpha channel is ignored and the image is composited as fully opaque. */
    VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR(0x00000001),

    /** The colour channels are already multiplied by the alpha channel. */
    VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR(0x00000002),

    /** The colour channels are not multiplied by alpha; the compositor does that. */
    VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR(0x00000004),

    /** Alpha handling is decided outside Vulkan, by native window system code. */
    VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR(0x00000008),
}

typealias VkCompositeAlphaFlagsKHR = VkFlags
