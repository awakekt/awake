/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

/**
 * A `VkSurfaceKHR` handle paired with the instance that owns it.
 *
 * @property instance The raw handle of the instance that created the surface.
 * @property surface The raw `VkSurfaceKHR` handle.
 */
data class VkSurfaceKHR(
    val instance: Long = 0,
    val surface: Long = 0,
)
