/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.vulkan.enums.VkPhysicalDeviceType

/**
 * The class Awake gives a device of this type. On a [handheld] platform (Android, iOS) a GPU reported
 * as integrated, virtual or other is a phone or tablet GPU, so it is [GpuDeviceClass.Mobile]: a phone's
 * Vulkan driver reports its GPU as integrated, which would otherwise give it a desktop's budgets
 * (#1688). Discrete and CPU devices keep their class anywhere.
 */
internal fun VkPhysicalDeviceType.toGpuDeviceClass(handheld: Boolean = runsOnHandheld): GpuDeviceClass = when (this) {
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> GpuDeviceClass.Discrete
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_CPU -> GpuDeviceClass.Software
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU,
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU,
    -> if (handheld) GpuDeviceClass.Mobile else GpuDeviceClass.Integrated
    VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_OTHER -> if (handheld) GpuDeviceClass.Mobile else GpuDeviceClass.Unknown
}

/** Whether this platform is a phone or tablet one: Android or iOS. */
internal expect val runsOnHandheld: Boolean
