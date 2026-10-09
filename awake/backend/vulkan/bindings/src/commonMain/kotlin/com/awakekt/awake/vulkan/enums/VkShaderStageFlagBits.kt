/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The pipeline stages a shader or resource binding applies to (`VkShaderStageFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkShaderStageFlagBits(val value: Int) {
    /** The vertex stage. */
    VERTEX(0x00000001),

    /** The tessellation control stage. */
    TESSELLATION_CONTROL(0x00000002),

    /** The tessellation evaluation stage. */
    TESSELLATION_EVALUATION(0x00000004),

    /** The geometry stage. */
    GEOMETRY(0x00000008),

    /** The fragment stage. */
    FRAGMENT(0x00000010),

    /** The compute stage. */
    COMPUTE(0x00000020),

    /** Every graphics stage: vertex, tessellation, geometry and fragment. */
    ALL_GRAPHICS(0x0000001F),

    /** Every stage, including any that extensions add. */
    ALL(0x7FFFFFFF),

    /** The ray generation stage. */
    RAYGEN_KHR(0x00000100),

    /** The any-hit stage of ray tracing. */
    ANY_HIT_KHR(0x00000200),

    /** The closest-hit stage of ray tracing. */
    CLOSEST_HIT_KHR(0x00000400),

    /** The miss stage of ray tracing. */
    MISS_KHR(0x00000800),

    /** The intersection stage of ray tracing. */
    INTERSECTION_KHR(0x00001000),

    /** The callable stage of ray tracing. */
    CALLABLE_KHR(0x00002000),

    /** The task stage of mesh shading (NVIDIA extension). */
    TASK_NV(0x00000040),

    /** The mesh stage of mesh shading (NVIDIA extension). */
    MESH_NV(0x00000080),

    /** The subpass shading stage (Huawei extension). */
    SUBPASS_SHADING_HUAWEI(0x00004000),

    /** Alias of [RAYGEN_KHR]; both names carry the same value. */
    RAYGEN_NV(RAYGEN_KHR.value),

    /** Alias of [ANY_HIT_KHR]; both names carry the same value. */
    ANY_HIT_NV(ANY_HIT_KHR.value),

    /** Alias of [CLOSEST_HIT_KHR]; both names carry the same value. */
    CLOSEST_HIT_NV(CLOSEST_HIT_KHR.value),

    /** Alias of [MISS_KHR]; both names carry the same value. */
    MISS_NV(MISS_KHR.value),

    /** Alias of [INTERSECTION_KHR]; both names carry the same value. */
    INTERSECTION_NV(INTERSECTION_KHR.value),

    /** Alias of [CALLABLE_KHR]; both names carry the same value. */
    CALLABLE_NV(CALLABLE_KHR.value),
    ;

    /** Lookup of a constant from its raw integer value. */
    companion object {
        /**
         * Returns the first constant whose value is [value]. Aliases share a value, so the first
         * listed wins.
         *
         * @param value The raw integer value.
         * @throws IllegalArgumentException If no constant has that value.
         */
        fun fromValue(value: Int) =
            values().find { it.value == value }
                ?: throw IllegalArgumentException("Unknown VkShaderStageFlagBits value: $value")
    }
}
