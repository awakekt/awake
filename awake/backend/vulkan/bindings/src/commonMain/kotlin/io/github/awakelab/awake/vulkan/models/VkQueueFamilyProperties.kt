/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models

import io.github.awakelab.awake.vulkan.VkMutator
import io.github.awakelab.awake.vulkan.enums.VkQueueFlags
import kotlin.jvm.JvmOverloads

@VkMutator
data class VkQueueFamilyProperties @JvmOverloads constructor(
    val queueFlags: VkQueueFlags = 0,
    val queueCount: UInt = 0u,
    val timestampValidBits: UInt = 0u,
    val minImageTransferGranularity: VkExtent3D = VkExtent3D(),
)
