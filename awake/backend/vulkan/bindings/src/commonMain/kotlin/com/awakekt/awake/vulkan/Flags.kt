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

/** Converts this single sample-count bit to a flags mask that contains only it. */
fun VkSampleCountFlagBits.toFlags(): VkSampleCountFlags = value

/**
 * Returns whether this mask has [bit] set.
 *
 * @param bit The flag to look for.
 */
infix fun VkFlags.has(bit: VkEnum): Boolean = this and bit.value != 0

/**
 * Returns whether this mask has the sample count [bit] set.
 *
 * @param bit The sample-count flag to look for.
 */
infix fun VkFlags.has(bit: VkSampleCountFlagBits): Boolean = this and bit.value != 0

/**
 * Returns whether this mask has the queue capability [bit] set.
 *
 * @param bit The queue flag to look for.
 */
infix fun VkFlags.has(bit: VkQueueFlagBits): Boolean = this and bit.value != 0

/**
 * Returns whether this mask has the composite-alpha mode [bit] set.
 *
 * @param bit The composite-alpha flag to look for.
 */
infix fun VkFlags.has(bit: VkCompositeAlphaFlagBitsKHR): Boolean = this and bit.value != 0

/**
 * Returns whether this mask has the image usage [bit] set.
 *
 * @param bit The image-usage flag to look for.
 */
infix fun VkFlags.has(bit: VkImageUsageFlagBits): Boolean = this and bit.value != 0

/**
 * Returns whether this mask has the surface transform [bit] set.
 *
 * @param bit The surface-transform flag to look for.
 */
infix fun VkFlags.has(bit: VkSurfaceTransformFlagBitsKHR): Boolean = this and bit.value != 0

/**
 * Returns this sample-count mask with [bit] added; the receiver is not changed.
 *
 * @param bit The sample-count flag to add.
 */
fun VkSampleCountFlags.set(bit: VkSampleCountFlagBits): VkSampleCountFlags = this or bit.value

/**
 * Returns this sample-count mask with [bit] removed; the receiver is not changed.
 *
 * @param bit The sample-count flag to remove.
 */
fun VkSampleCountFlags.clear(bit: VkSampleCountFlagBits): VkSampleCountFlags = this and bit.value.inv()
