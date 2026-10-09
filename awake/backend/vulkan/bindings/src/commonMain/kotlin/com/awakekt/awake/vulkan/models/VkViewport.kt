/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

/**
 * A viewport transform from normalized device coordinates to framebuffer coordinates
 * (`VkViewport`).
 *
 * @property x The left edge of the viewport, in pixels.
 * @property y The top edge of the viewport, in pixels.
 * @property width The viewport width, in pixels.
 * @property height The viewport height, in pixels.
 * @property minDepth The depth that maps to the near plane.
 * @property maxDepth The depth that maps to the far plane.
 */
data class VkViewport(
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val width: Float = 0.0f,
    val height: Float = 0.0f,
    val minDepth: Float = 0.0f,
    val maxDepth: Float = 1.0f,
)
