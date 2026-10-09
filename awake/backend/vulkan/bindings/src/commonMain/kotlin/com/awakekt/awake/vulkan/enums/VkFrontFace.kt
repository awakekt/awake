/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * Which vertex winding makes a triangle front-facing (`VkFrontFace`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkFrontFace(val value: Int) {
    /** A triangle with positive area in framebuffer space is front-facing. */
    VK_FRONT_FACE_COUNTER_CLOCKWISE(0),

    /** A triangle with negative area in framebuffer space is front-facing. */
    VK_FRONT_FACE_CLOCKWISE(1),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_FRONT_FACE_MAX_ENUM(0x7FFFFFFF),
}
