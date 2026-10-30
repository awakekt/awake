/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.vulkan.Vulkan

/** Counts supported for both fixed/floating colour and depth attachments on this device. */
internal fun GraphicsDevice.framebufferSampleCounts(): Set<Int> {
    val limits = Vulkan.vkGetPhysicalDeviceProperties(physicalDevice).limits
    val supported = limits.framebufferColorSampleCounts and limits.framebufferDepthSampleCounts
    return setOf(1, 2, 4, 8).filterTo(mutableSetOf()) { it and supported != 0 }
}
