/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.utils

import com.awakekt.awake.vulkan.enums.VkResult

/**
 * Thrown when a Vulkan call returns a failure `VkResult`.
 *
 * @property message Names the failed call and the result.
 * @property result The `VkResult` the driver returned, so a caller can react to a specific one
 * such as `VK_ERROR_OUT_OF_DATE_KHR`.
 */
data class VkResultException(override val message: String, val result: VkResult) : RuntimeException()
