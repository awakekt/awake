/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.renderer.GpuDeviceClass
import com.awakekt.awake.vulkan.enums.VkPhysicalDeviceType

/** PCI vendor ids of GPUs found only in phones and tablets: Arm, Qualcomm, Imagination, Samsung. */
private val MOBILE_VENDORS = setOf(0x13B5, 0x5143, 0x1010, 0x144D)

/**
 * The [GpuDeviceClass] of a Vulkan device. A phone GPU reports itself as integrated, so its vendor
 * decides: Arm, Qualcomm, Imagination and Samsung make no desktop GPUs.
 *
 * @param type The device's reported type.
 * @param vendorId The device's PCI vendor id.
 * @return The device's class.
 */
internal fun gpuDeviceClass(type: VkPhysicalDeviceType, vendorId: Int): GpuDeviceClass = when {
    type == VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_CPU -> GpuDeviceClass.Software
    vendorId in MOBILE_VENDORS -> GpuDeviceClass.Mobile
    type == VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> GpuDeviceClass.Discrete
    type == VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU ||
        type == VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU -> GpuDeviceClass.Integrated
    else -> GpuDeviceClass.Unknown
}
