/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

/**
 * A `VkSwapchainKHR` handle with the type information a plain `Long` lacks.
 *
 * @property swapchain The raw `VkSwapchainKHR` handle.
 */
data class VkSwapchainKHR(val swapchain: Long = 0)
