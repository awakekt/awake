/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

/**
 * A rectangle given by an offset and an extent (`VkRect2D`).
 *
 * @property offset The top-left corner of the rectangle.
 * @property extent The width and height of the rectangle.
 */
data class VkRect2D(
    val offset: VkOffset2D = VkOffset2D(),
    val extent: VkExtent2D = VkExtent2D(),
)
