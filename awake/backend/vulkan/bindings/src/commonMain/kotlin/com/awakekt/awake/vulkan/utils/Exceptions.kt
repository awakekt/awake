/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.utils

import com.awakekt.awake.vulkan.enums.VkResult

data class VkResultException(override val message: String, val result: VkResult) : RuntimeException()
