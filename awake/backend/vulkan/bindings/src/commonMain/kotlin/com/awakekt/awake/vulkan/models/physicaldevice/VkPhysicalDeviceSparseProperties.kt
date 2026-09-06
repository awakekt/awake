/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.physicaldevice

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkMutator
import kotlin.jvm.JvmOverloads

@VkMutator
data class VkPhysicalDeviceSparseProperties @JvmOverloads constructor(
    val residencyStandard2DBlockShape: VkBool32 = false,
    val residencyStandard2DMultisampleBlockShape: VkBool32 = false,
    val residencyStandard3DBlockShape: VkBool32 = false,
    val residencyAlignedMipSize: VkBool32 = false,
    val residencyNonResidentStrict: VkBool32 = false,
)
