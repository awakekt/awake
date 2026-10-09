/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * Flags that change how a pipeline is created (`VkPipelineCreateFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPipelineCreateFlagBits(val value: Int) {
    /** Skip optimization, which makes creation faster at the cost of runtime speed. */
    VK_PIPELINE_CREATE_DISABLE_OPTIMIZATION_BIT(0x00000001),

    /** Other pipelines may be created as derivatives of this one. */
    VK_PIPELINE_CREATE_ALLOW_DERIVATIVES_BIT(0x00000002),

    /** This pipeline is a derivative of another. */
    VK_PIPELINE_CREATE_DERIVATIVE_BIT(0x00000004),

    /** The view index of each draw is taken from the device index. */
    VK_PIPELINE_CREATE_VIEW_INDEX_FROM_DEVICE_INDEX_BIT(0x00000008),

    /** Compute dispatches may use a non-zero workgroup base. */
    VK_PIPELINE_CREATE_DISPATCH_BASE_BIT(0x00000010),

    /**
     * Creation fails with `VK_PIPELINE_COMPILE_REQUIRED` instead of compiling when no cached result
     * matches.
     */
    VK_PIPELINE_CREATE_FAIL_ON_PIPELINE_COMPILE_REQUIRED_BIT(0x00000100),

    /** Creation of a batch stops at the first pipeline that fails. */
    VK_PIPELINE_CREATE_EARLY_RETURN_ON_FAILURE_BIT(0x00000200),

    /** The pipeline is used with a fragment shading rate attachment in dynamic rendering. */
    VK_PIPELINE_CREATE_RENDERING_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR(0x00200000),

    /** The pipeline is used with a fragment density map attachment in dynamic rendering. */
    VK_PIPELINE_CREATE_RENDERING_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT(0x00400000),

    /** No any-hit shader group may be null. */
    VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_ANY_HIT_SHADERS_BIT_KHR(0x00004000),

    /** No closest-hit shader group may be null. */
    VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_CLOSEST_HIT_SHADERS_BIT_KHR(0x00008000),

    /** No miss shader group may be null. */
    VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_MISS_SHADERS_BIT_KHR(0x00010000),

    /** No intersection shader group may be null. */
    VK_PIPELINE_CREATE_RAY_TRACING_NO_NULL_INTERSECTION_SHADERS_BIT_KHR(0x00020000),

    /** Ray traversal skips triangle geometry. */
    VK_PIPELINE_CREATE_RAY_TRACING_SKIP_TRIANGLES_BIT_KHR(0x00001000),

    /** Ray traversal skips axis-aligned bounding box geometry. */
    VK_PIPELINE_CREATE_RAY_TRACING_SKIP_AABBS_BIT_KHR(0x00002000),

    /** Shader group handles can be captured and replayed. */
    VK_PIPELINE_CREATE_RAY_TRACING_SHADER_GROUP_HANDLE_CAPTURE_REPLAY_BIT_KHR(0x00080000),

    /** Compilation may be deferred until the pipeline is first used. */
    VK_PIPELINE_CREATE_DEFER_COMPILE_BIT_NV(0x00000020),

    /** Executable statistics are captured and can be queried. */
    VK_PIPELINE_CREATE_CAPTURE_STATISTICS_BIT_KHR(0x00000040),

    /** Internal representations of the executables are captured and can be queried. */
    VK_PIPELINE_CREATE_CAPTURE_INTERNAL_REPRESENTATIONS_BIT_KHR(0x00000080),

    /** The pipeline can be bound by device-generated commands. */
    VK_PIPELINE_CREATE_INDIRECT_BINDABLE_BIT_NV(0x00040000),

    /** The pipeline is a library to be linked into other pipelines. */
    VK_PIPELINE_CREATE_LIBRARY_BIT_KHR(0x00000800),

    /** The pipeline may trace motion-blurred geometry. */
    VK_PIPELINE_CREATE_RAY_TRACING_ALLOW_MOTION_BIT_NV(0x00100000),

    /** Alias of [VK_PIPELINE_CREATE_DISPATCH_BASE_BIT]; both names carry the same value. */
    VK_PIPELINE_CREATE_DISPATCH_BASE(VK_PIPELINE_CREATE_DISPATCH_BASE_BIT.value),

    /**
     * Alias of [VK_PIPELINE_CREATE_RENDERING_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR]; both names
     * carry the same value.
     */
    VK_PIPELINE_RASTERIZATION_STATE_CREATE_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR(
        VK_PIPELINE_CREATE_RENDERING_FRAGMENT_SHADING_RATE_ATTACHMENT_BIT_KHR.value,
    ),

    /**
     * Alias of [VK_PIPELINE_CREATE_RENDERING_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT]; both names
     * carry the same value.
     */
    VK_PIPELINE_RASTERIZATION_STATE_CREATE_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT(
        VK_PIPELINE_CREATE_RENDERING_FRAGMENT_DENSITY_MAP_ATTACHMENT_BIT_EXT.value,
    ),

    /**
     * Alias of [VK_PIPELINE_CREATE_VIEW_INDEX_FROM_DEVICE_INDEX_BIT]; both names carry the same
     * value.
     */
    VK_PIPELINE_CREATE_VIEW_INDEX_FROM_DEVICE_INDEX_BIT_KHR(
        VK_PIPELINE_CREATE_VIEW_INDEX_FROM_DEVICE_INDEX_BIT.value,
    ),

    /** Alias of [VK_PIPELINE_CREATE_DISPATCH_BASE]; both names carry the same value. */
    VK_PIPELINE_CREATE_DISPATCH_BASE_KHR(VK_PIPELINE_CREATE_DISPATCH_BASE.value),

    /**
     * Alias of [VK_PIPELINE_CREATE_FAIL_ON_PIPELINE_COMPILE_REQUIRED_BIT]; both names carry the
     * same value.
     */
    VK_PIPELINE_CREATE_FAIL_ON_PIPELINE_COMPILE_REQUIRED_BIT_EXT(
        VK_PIPELINE_CREATE_FAIL_ON_PIPELINE_COMPILE_REQUIRED_BIT.value,
    ),

    /**
     * Alias of [VK_PIPELINE_CREATE_EARLY_RETURN_ON_FAILURE_BIT]; both names carry the same value.
     */
    VK_PIPELINE_CREATE_EARLY_RETURN_ON_FAILURE_BIT_EXT(
        VK_PIPELINE_CREATE_EARLY_RETURN_ON_FAILURE_BIT.value,
    ),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_PIPELINE_CREATE_FLAG_BITS_MAX_ENUM(0x7FFFFFFF),
}

typealias VkPipelineCreateFlags = VkFlags
