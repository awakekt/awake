/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * How often a vertex binding advances (`VkVertexInputRate`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkVertexInputRate(val value: Int) {
    /** The binding advances once per vertex. */
    VK_VERTEX_INPUT_RATE_VERTEX(0),

    /** The binding advances once per instance. */
    VK_VERTEX_INPUT_RATE_INSTANCE(1),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_VERTEX_INPUT_RATE_MAX_ENUM(0x7FFFFFFF),
}
