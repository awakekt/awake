/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.enums.VkCompositeAlphaFlagBitsKHR
import com.awakekt.awake.vulkan.enums.VkEnum
import com.awakekt.awake.vulkan.enums.VkImageUsageFlagBits
import com.awakekt.awake.vulkan.enums.VkQueueFlagBits
import com.awakekt.awake.vulkan.enums.VkSampleCountFlagBits
import com.awakekt.awake.vulkan.enums.VkSampleCountFlags
import com.awakekt.awake.vulkan.enums.VkSurfaceTransformFlagBitsKHR

fun VkSampleCountFlagBits.toFlags(): VkSampleCountFlags = value

infix fun VkFlags.has(bit: VkEnum): Boolean = this and bit.value != 0

infix fun VkFlags.has(bit: VkSampleCountFlagBits): Boolean = this and bit.value != 0

infix fun VkFlags.has(bit: VkQueueFlagBits): Boolean = this and bit.value != 0

infix fun VkFlags.has(bit: VkCompositeAlphaFlagBitsKHR): Boolean = this and bit.value != 0

infix fun VkFlags.has(bit: VkImageUsageFlagBits): Boolean = this and bit.value != 0

infix fun VkFlags.has(bit: VkSurfaceTransformFlagBitsKHR): Boolean = this and bit.value != 0

fun VkSampleCountFlags.set(bit: VkSampleCountFlagBits): VkSampleCountFlags = this or bit.value

fun VkSampleCountFlags.clear(bit: VkSampleCountFlagBits): VkSampleCountFlags = this and bit.value.inv()
