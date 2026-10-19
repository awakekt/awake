/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.vulkan.enums.VkPhysicalDeviceType

internal fun VkPhysicalDeviceType.toGpuDeviceClass(): GpuDeviceClass = when (this) {
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> GpuDeviceClass.Discrete
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> GpuDeviceClass.Integrated
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> GpuDeviceClass.Integrated
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_CPU -> GpuDeviceClass.Software
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_OTHER -> GpuDeviceClass.Unknown
}
