/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

/**
 * The memory accesses a barrier or subpass dependency makes visible or waits for
 * (`VkAccessFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkAccessFlagBits(val value: Int) {
    /** Reads of indirect draw or dispatch command data. */
    VK_ACCESS_INDIRECT_COMMAND_READ_BIT(0x00000001),

    /** Reads of an index buffer. */
    VK_ACCESS_INDEX_READ_BIT(0x00000002),

    /** Reads of vertex attribute data. */
    VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT(0x00000004),

    /** Reads of a uniform buffer. */
    VK_ACCESS_UNIFORM_READ_BIT(0x00000008),

    /** Reads of an input attachment within a render pass. */
    VK_ACCESS_INPUT_ATTACHMENT_READ_BIT(0x00000010),

    /**
     * Reads from a storage buffer, uniform texel buffer, storage texel buffer, sampled image or
     * storage image.
     */
    VK_ACCESS_SHADER_READ_BIT(0x00000020),

    /** Writes to a storage buffer, storage texel buffer or storage image. */
    VK_ACCESS_SHADER_WRITE_BIT(0x00000040),

    /** Reads of a colour attachment, such as by blending or logic ops. */
    VK_ACCESS_COLOR_ATTACHMENT_READ_BIT(0x00000080),

    /** Writes to a colour or resolve attachment during a render pass. */
    VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT(0x00000100),

    /** Reads of a depth/stencil attachment by depth or stencil operations. */
    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT(0x00000200),

    /** Writes to a depth/stencil attachment by depth or stencil operations. */
    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT(0x00000400),

    /** Reads by a transfer operation such as a copy or blit source. */
    VK_ACCESS_TRANSFER_READ_BIT(0x00000800),

    /** Writes by a transfer operation such as a copy or clear destination. */
    VK_ACCESS_TRANSFER_WRITE_BIT(0x00001000),

    /** Reads by the host. */
    VK_ACCESS_HOST_READ_BIT(0x00002000),

    /** Writes by the host. */
    VK_ACCESS_HOST_WRITE_BIT(0x00004000),

    /** Any read of memory. */
    VK_ACCESS_MEMORY_READ_BIT(0x00008000),

    /** Any write to memory. */
    VK_ACCESS_MEMORY_WRITE_BIT(0x00010000),

    /** No accesses. */
    VK_ACCESS_NONE(0),

    /** Writes to a transform feedback buffer. */
    VK_ACCESS_TRANSFORM_FEEDBACK_WRITE_BIT_EXT(0x02000000),

    /** Reads of a transform feedback counter buffer. */
    VK_ACCESS_TRANSFORM_FEEDBACK_COUNTER_READ_BIT_EXT(0x04000000),

    /** Writes to a transform feedback counter buffer. */
    VK_ACCESS_TRANSFORM_FEEDBACK_COUNTER_WRITE_BIT_EXT(0x08000000),

    /** Reads of a predicate buffer used for conditional rendering. */
    VK_ACCESS_CONDITIONAL_RENDERING_READ_BIT_EXT(0x00100000),

    /**
     * Reads of a colour attachment with advanced blend operations, without coherency guarantees.
     */
    VK_ACCESS_COLOR_ATTACHMENT_READ_NONCOHERENT_BIT_EXT(0x00080000),

    /** Reads of an acceleration structure. */
    VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR(0x00200000),

    /** Writes to an acceleration structure. */
    VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR(0x00400000),

    /** Reads of a fragment density map. */
    VK_ACCESS_FRAGMENT_DENSITY_MAP_READ_BIT_EXT(0x01000000),

    /** Reads of a fragment shading rate attachment. */
    VK_ACCESS_FRAGMENT_SHADING_RATE_ATTACHMENT_READ_BIT_KHR(0x00800000),

    /** Reads of device-generated command preprocess data. */
    VK_ACCESS_COMMAND_PREPROCESS_READ_BIT_NV(0x00020000),

    /** Writes of device-generated command preprocess data. */
    VK_ACCESS_COMMAND_PREPROCESS_WRITE_BIT_NV(0x00040000),

    /**
     * Alias of [VK_ACCESS_FRAGMENT_SHADING_RATE_ATTACHMENT_READ_BIT_KHR]; both names carry the same
     * value.
     */
    VK_ACCESS_SHADING_RATE_IMAGE_READ_BIT_NV(VK_ACCESS_FRAGMENT_SHADING_RATE_ATTACHMENT_READ_BIT_KHR.value),

    /**
     * Alias of [VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR]; both names carry the same value.
     */
    VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_NV(VK_ACCESS_ACCELERATION_STRUCTURE_READ_BIT_KHR.value),

    /**
     * Alias of [VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR]; both names carry the same value.
     */
    VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_NV(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR.value),

    /** Alias of [VK_ACCESS_NONE]; both names carry the same value. */
    VK_ACCESS_NONE_KHR(VK_ACCESS_NONE.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_ACCESS_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkAccessFlags = Int
