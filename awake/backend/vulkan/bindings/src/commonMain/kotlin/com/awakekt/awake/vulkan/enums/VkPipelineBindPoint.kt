/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * Which kind of pipeline a bind command targets (`VkPipelineBindPoint`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPipelineBindPoint(val value: Int) {
    /** Graphics pipelines. */
    VK_PIPELINE_BIND_POINT_GRAPHICS(0),

    /** Compute pipelines. */
    VK_PIPELINE_BIND_POINT_COMPUTE(1),

    /** Ray tracing pipelines. */
    VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR(1000165000),

    /** Subpass shading pipelines (Huawei extension). */
    VK_PIPELINE_BIND_POINT_SUBPASS_SHADING_HUAWEI(1000369003),

    /** Alias of [VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR]; both names carry the same value. */
    VK_PIPELINE_BIND_POINT_RAY_TRACING_NV(VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_PIPELINE_BIND_POINT_MAX_ENUM(0x7FFFFFFF),
}
