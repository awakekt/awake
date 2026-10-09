/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkQueueFlags
import kotlin.jvm.JvmOverloads

/**
 * The capabilities of one queue family of a physical device (`VkQueueFamilyProperties`).
 *
 * @property queueFlags A mask of the kinds of work the family supports, built from queue flag
 * constants.
 * @property queueCount The number of queues in the family.
 * @property timestampValidBits How many bits of a timestamp the family writes; 0 means it cannot
 * write timestamps.
 * @property minImageTransferGranularity The smallest image region, in texels, a transfer command on
 * the family can copy.
 */
@VkMutator
data class VkQueueFamilyProperties @JvmOverloads constructor(
    val queueFlags: VkQueueFlags = 0,
    val queueCount: UInt = 0u,
    val timestampValidBits: UInt = 0u,
    val minImageTransferGranularity: VkExtent3D = VkExtent3D(),
)
