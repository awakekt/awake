/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

// Representing VkImageUsageFlagBits as an enum class
/**
 * What an image may be used for; every use must be declared when the image is created
 * (`VkImageUsageFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkImageUsageFlagBits(val value: Int) {
    /** The image can be the source of a transfer command. */
    VK_IMAGE_USAGE_TRANSFER_SRC_BIT(0x00000001),

    /** The image can be the destination of a transfer command. */
    VK_IMAGE_USAGE_TRANSFER_DST_BIT(0x00000002),

    /** The image can be sampled from a shader. */
    VK_IMAGE_USAGE_SAMPLED_BIT(0x00000004),

    /** The image can be used as a storage image in a shader. */
    VK_IMAGE_USAGE_STORAGE_BIT(0x00000008),

    /** The image can be a colour or resolve attachment. */
    VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT(0x00000010),

    /** The image can be a depth/stencil attachment. */
    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT(0x00000020),

    /** The image's memory may be allocated lazily, for attachments that never leave tile memory. */
    VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT(0x00000040),

    /** The image can be read as an input attachment within a render pass. */
    VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT(0x00000080),

    /** The image can be the output of a video decode operation. */
    VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR(0x00000400),

    /** The image can be an input of a video decode operation. */
    VK_IMAGE_USAGE_VIDEO_DECODE_SRC_BIT_KHR(0x00000800),

    /** The image can be a decoded picture buffer reference for video decode. */
    VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR(0x00001000),

    /** The image can be a fragment density map. */
    VK_IMAGE_USAGE_FRAGMENT_DENSITY_MAP_BIT_EXT(0x00000200),

    /** The image can be a fragment shading rate attachment. */
    VK_IMAGE_USAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR(0x00000100),

    /** The image can be the output of a video encode operation. */
    VK_IMAGE_USAGE_VIDEO_ENCODE_DST_BIT_KHR(0x00002000),

    /** The image can be an input of a video encode operation. */
    VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR(0x00004000),

    /** The image can be a decoded picture buffer reference for video encode. */
    VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR(0x00008000),

    /** The image can be an invocation mask for ray tracing (Huawei extension). */
    VK_IMAGE_USAGE_INVOCATION_MASK_BIT_HUAWEI(0x00040000),

    /**
     * Alias of [VK_IMAGE_USAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR]; both names carry the same
     * value.
     */
    VK_IMAGE_USAGE_SHADING_RATE_IMAGE_BIT_NV(VK_IMAGE_USAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_IMAGE_USAGE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

// Representing VkImageUsageFlags as a typealias of Int
typealias VkImageUsageFlags = VkFlags
