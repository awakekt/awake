/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models

import io.github.awakelab.awake.vulkan.VkMutator
import kotlin.jvm.JvmOverloads

@VkMutator
data class VkExtent3D @JvmOverloads constructor(
    val width: Int = 0,
    val height: Int = 0,
    val depth: Int = 0,
)
