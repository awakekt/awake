/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * The number of samples per pixel (`VkSampleCountFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkSampleCountFlagBits(val value: Int) {
    /** 1 sample per pixel. */
    VK_SAMPLE_COUNT_1_BIT(0x00000001),

    /** 2 samples per pixel. */
    VK_SAMPLE_COUNT_2_BIT(0x00000002),

    /** 4 samples per pixel. */
    VK_SAMPLE_COUNT_4_BIT(0x00000004),

    /** 8 samples per pixel. */
    VK_SAMPLE_COUNT_8_BIT(0x00000008),

    /** 16 samples per pixel. */
    VK_SAMPLE_COUNT_16_BIT(0x00000010),

    /** 32 samples per pixel. */
    VK_SAMPLE_COUNT_32_BIT(0x00000020),

    /** 64 samples per pixel. */
    VK_SAMPLE_COUNT_64_BIT(0x00000040),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_SAMPLE_COUNT_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkSampleCountFlags = VkFlags
