/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

import com.awakekt.awake.vulkan.VkFlags

/**
 * Flags that change how a pipeline shader stage is created (`VkPipelineShaderStageCreateFlagBits`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkPipelineShaderStageCreateFlagBits(val value: Int) {
    /** The subgroup size may vary between invocations of the stage. */
    ALLOW_VARYING_SUBGROUP_SIZE(0x00000001),

    /** Compute workgroups must be launched with full subgroups. */
    REQUIRE_FULL_SUBGROUPS(0x00000002),

    /** Alias of [ALLOW_VARYING_SUBGROUP_SIZE]; both names carry the same value. */
    ALLOW_VARYING_SUBGROUP_SIZE_EXT(ALLOW_VARYING_SUBGROUP_SIZE.value),

    /** Alias of [REQUIRE_FULL_SUBGROUPS]; both names carry the same value. */
    REQUIRE_FULL_SUBGROUPS_EXT(REQUIRE_FULL_SUBGROUPS.value),
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
                ?: throw IllegalArgumentException("Unknown VkPipelineShaderStageCreateFlagBits value: $value")
    }
}

typealias VkPipelineShaderStageCreateFlags = VkFlags // <VkPipelineShaderStageCreateFlagBits>
