/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums.flags

import com.awakekt.awake.vulkan.VkFlags

/**
 * The pipeline stages a barrier or dependency waits for or blocks (`VkPipelineStageFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPipelineStageFlagBits(val value: Int) {
    /** The start of the pipeline, before any work. */
    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT(0x00000001),

    /** The stage where indirect draw and dispatch parameters are read. */
    VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT(0x00000002),

    /** The stage where vertex and index buffers are consumed. */
    VK_PIPELINE_STAGE_VERTEX_INPUT_BIT(0x00000004),

    /** The vertex shader stage. */
    VK_PIPELINE_STAGE_VERTEX_SHADER_BIT(0x00000008),

    /** The tessellation control shader stage. */
    VK_PIPELINE_STAGE_TESSELLATION_CONTROL_SHADER_BIT(0x00000010),

    /** The tessellation evaluation shader stage. */
    VK_PIPELINE_STAGE_TESSELLATION_EVALUATION_SHADER_BIT(0x00000020),

    /** The geometry shader stage. */
    VK_PIPELINE_STAGE_GEOMETRY_SHADER_BIT(0x00000040),

    /** The fragment shader stage. */
    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT(0x00000080),

    /** The stage where depth and stencil tests run before the fragment shader. */
    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT(0x00000100),

    /** The stage where depth and stencil tests run after the fragment shader. */
    VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT(0x00000200),

    /** The stage where blended colour values are written to attachments. */
    VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT(0x00000400),

    /** The compute shader stage. */
    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT(0x00000800),

    /** Copy, blit, resolve and clear commands. */
    VK_PIPELINE_STAGE_TRANSFER_BIT(0x00001000),

    /** The end of the pipeline, after all work. */
    VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT(0x00002000),

    /** Host reads and writes of device memory. */
    VK_PIPELINE_STAGE_HOST_BIT(0x00004000),

    /** Every graphics pipeline stage. */
    VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT(0x00008000),

    /** Every stage of every command. */
    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT(0x00010000),

    /** No stages. */
    VK_PIPELINE_STAGE_NONE(0),

    /** The stage where transform feedback values are written. */
    VK_PIPELINE_STAGE_TRANSFORM_FEEDBACK_BIT_EXT(0x01000000),

    /** The stage where the predicate for conditional rendering is read. */
    VK_PIPELINE_STAGE_CONDITIONAL_RENDERING_BIT_EXT(0x00040000),

    /** Acceleration structure build and copy commands. */
    VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR(0x02000000),

    /** The ray tracing shader stages. */
    VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR(0x00200000),

    /** The task shader stage (NVIDIA mesh shading). */
    VK_PIPELINE_STAGE_TASK_SHADER_BIT_NV(0x00080000),

    /** The mesh shader stage (NVIDIA mesh shading). */
    VK_PIPELINE_STAGE_MESH_SHADER_BIT_NV(0x00100000),

    /** The stage where a fragment density map is read. */
    VK_PIPELINE_STAGE_FRAGMENT_DENSITY_PROCESS_BIT_EXT(0x00800000),

    /** The stage where a fragment shading rate attachment is read. */
    VK_PIPELINE_STAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR(0x00400000),

    /** The stage where device-generated commands are preprocessed. */
    VK_PIPELINE_STAGE_COMMAND_PREPROCESS_BIT_NV(0x00020000),

    /**
     * Alias of [VK_PIPELINE_STAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR]; both names carry the
     * same value.
     */
    VK_PIPELINE_STAGE_SHADING_RATE_IMAGE_BIT_NV(
        VK_PIPELINE_STAGE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR.value,
    ),

    /** Alias of [VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR]; both names carry the same value. */
    VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_NV(VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR.value),

    /**
     * Alias of [VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR]; both names carry the same
     * value.
     */
    VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_NV(
        VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR.value,
    ),

    /** Alias of [VK_PIPELINE_STAGE_NONE]; both names carry the same value. */
    VK_PIPELINE_STAGE_NONE_KHR(VK_PIPELINE_STAGE_NONE.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_PIPELINE_STAGE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkPipelineStageFlags = VkFlags
