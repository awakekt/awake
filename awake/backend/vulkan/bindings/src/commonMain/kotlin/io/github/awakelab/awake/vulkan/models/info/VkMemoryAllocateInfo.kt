/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models.info

import io.github.awakelab.awake.vulkan.VkDeviceSize

/**
 * Marshalled by jni-binding-generator (see docs/decisions/D10-codegen-derisk-findings.md).
 * Deliberately omits sType/pNext — same reasoning as VkBufferCreateInfo.
 */
class VkMemoryAllocateInfo(
    val allocationSize: VkDeviceSize,
    val memoryTypeIndex: Int,
)
