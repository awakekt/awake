/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

/**
 * A two-dimensional offset from an origin (`VkOffset2D`).
 *
 * @property x The horizontal offset.
 * @property y The vertical offset.
 */
data class VkOffset2D(
    val x: Int = 0,
    val y: Int = 0,
)
