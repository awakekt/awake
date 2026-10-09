/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.physicaldevice

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkMutator
import kotlin.jvm.JvmOverloads

/**
 * Sparse-resource behaviours of a physical device (`VkPhysicalDeviceSparseProperties`).
 *
 * @property residencyStandard2DBlockShape Whether the device returns the standard 2D sparse block
 * shapes for single-sample images.
 * @property residencyStandard2DMultisampleBlockShape Whether the device returns the standard 2D
 * sparse block shapes for multisampled images.
 * @property residencyStandard3DBlockShape Whether the device returns the standard 3D sparse block
 * shapes.
 * @property residencyAlignedMipSize Whether images whose mip tail size is not a multiple of the
 * sparse block size are supported.
 * @property residencyNonResidentStrict Whether the device treats reads of non-resident sparse
 * memory as defined, returning zero.
 */
@VkMutator
data class VkPhysicalDeviceSparseProperties @JvmOverloads constructor(
    val residencyStandard2DBlockShape: VkBool32 = false,
    val residencyStandard2DMultisampleBlockShape: VkBool32 = false,
    val residencyStandard3DBlockShape: VkBool32 = false,
    val residencyAlignedMipSize: VkBool32 = false,
    val residencyNonResidentStrict: VkBool32 = false,
)
