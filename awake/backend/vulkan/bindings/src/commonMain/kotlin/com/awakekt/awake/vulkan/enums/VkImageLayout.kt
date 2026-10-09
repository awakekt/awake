/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How an image's memory is arranged for a kind of access (`VkImageLayout`); transitions between
 * layouts are explicit.
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkImageLayout(val value: Int) {
    /** The contents are undefined, so a transition from it may discard the image's data. */
    VK_IMAGE_LAYOUT_UNDEFINED(0),

    /** Supports every kind of access, but is not optimal for any of them. */
    VK_IMAGE_LAYOUT_GENERAL(1),

    /** Optimal for use as a colour or resolve attachment. */
    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL(2),

    /** Optimal for use as a depth/stencil attachment. */
    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL(3),

    /** Optimal for read-only depth/stencil access, as an attachment or in a shader. */
    VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL(4),

    /** Optimal for sampling in a shader or reading as an input attachment. */
    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL(5),

    /** Optimal for use as the source of a transfer command. */
    VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL(6),

    /** Optimal for use as the destination of a transfer command. */
    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL(7),

    /**
     * The contents are undefined, but data the host wrote is preserved; only valid for linear
     * images.
     */
    VK_IMAGE_LAYOUT_PREINITIALIZED(8),

    /** The depth aspect is read-only and the stencil aspect is an attachment. */
    VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL(1000117000),

    /** The depth aspect is an attachment and the stencil aspect is read-only. */
    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL(1000117001),

    /** Optimal for use as a depth attachment, for images with a depth aspect. */
    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL(1000241000),

    /** Optimal for read-only depth access, as an attachment or in a shader. */
    VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL(1000241001),

    /** Optimal for use as a stencil attachment, for images with a stencil aspect. */
    VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL(1000241002),

    /** Optimal for read-only stencil access, as an attachment or in a shader. */
    VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL(1000241003),

    /** Optimal for any read-only use of the image. */
    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL(1000314000),

    /** Optimal for use as any kind of attachment: colour, depth or stencil. */
    VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL(1000314001),

    /**
     * Ready to be presented by a swapchain; the layout a swapchain image rests in between frames.
     */
    VK_IMAGE_LAYOUT_PRESENT_SRC_KHR(1000001002),

    /** Optimal for use as the output picture of a video decode operation. */
    VK_IMAGE_LAYOUT_VIDEO_DECODE_DST_KHR(1000024000),

    /** Optimal for use as an input picture of a video decode operation. */
    VK_IMAGE_LAYOUT_VIDEO_DECODE_SRC_KHR(1000024001),

    /** Optimal for use as a decoded picture buffer reference during video decode. */
    VK_IMAGE_LAYOUT_VIDEO_DECODE_DPB_KHR(1000024002),

    /**
     * A shared presentable image that the application and presentation engine may use at the same
     * time.
     */
    VK_IMAGE_LAYOUT_SHARED_PRESENT_KHR(1000111000),

    /** Optimal for use as a fragment density map attachment. */
    VK_IMAGE_LAYOUT_FRAGMENT_DENSITY_MAP_OPTIMAL_EXT(1000218000),

    /** Optimal for use as a fragment shading rate attachment. */
    VK_IMAGE_LAYOUT_FRAGMENT_SHADING_RATE_ATTACHMENT_OPTIMAL_KHR(1000164003),

    /** Optimal for use as the output bitstream image of a video encode operation. */
    VK_IMAGE_LAYOUT_VIDEO_ENCODE_DST_KHR(1000299000),

    /** Optimal for use as the input picture of a video encode operation. */
    VK_IMAGE_LAYOUT_VIDEO_ENCODE_SRC_KHR(1000299001),

    /** Optimal for use as a decoded picture buffer reference during video encode. */
    VK_IMAGE_LAYOUT_VIDEO_ENCODE_DPB_KHR(1000299002),

    /**
     * Alias of [VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL]; both names carry the
     * same value.
     */
    VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL_KHR(
        VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_STENCIL_ATTACHMENT_OPTIMAL.value,
    ),

    /**
     * Alias of [VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL]; both names carry the
     * same value.
     */
    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL_KHR(
        VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_STENCIL_READ_ONLY_OPTIMAL.value,
    ),

    /**
     * Alias of [VK_IMAGE_LAYOUT_FRAGMENT_SHADING_RATE_ATTACHMENT_OPTIMAL_KHR]; both names carry the
     * same value.
     */
    VK_IMAGE_LAYOUT_SHADING_RATE_OPTIMAL_NV(
        VK_IMAGE_LAYOUT_FRAGMENT_SHADING_RATE_ATTACHMENT_OPTIMAL_KHR.value,
    ),

    /** Alias of [VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL]; both names carry the same value. */
    VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL_KHR(VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL.value),

    /** Alias of [VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL]; both names carry the same value. */
    VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL_KHR(VK_IMAGE_LAYOUT_DEPTH_READ_ONLY_OPTIMAL.value),

    /** Alias of [VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL]; both names carry the same value. */
    VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL_KHR(VK_IMAGE_LAYOUT_STENCIL_ATTACHMENT_OPTIMAL.value),

    /** Alias of [VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL]; both names carry the same value. */
    VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL_KHR(VK_IMAGE_LAYOUT_STENCIL_READ_ONLY_OPTIMAL.value),

    /** Alias of [VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL]; both names carry the same value. */
    VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL_KHR(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL.value),

    /** Alias of [VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL]; both names carry the same value. */
    VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_IMAGE_LAYOUT_MAX_ENUM(0x7FFFFFFF),
}
