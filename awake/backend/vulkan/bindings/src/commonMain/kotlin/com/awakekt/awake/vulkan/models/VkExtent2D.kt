/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkMutator
import kotlin.jvm.JvmOverloads

/**
 * A two-dimensional size, usually in pixels (`VkExtent2D`).
 *
 * @property width The horizontal size.
 * @property height The vertical size.
 */
@VkMutator
data class VkExtent2D @JvmOverloads constructor(
    val width: Int = 0,
    val height: Int = 0,
)
