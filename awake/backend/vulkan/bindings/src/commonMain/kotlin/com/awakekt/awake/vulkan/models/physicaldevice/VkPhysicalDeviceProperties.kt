/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.physicaldevice

import com.awakekt.awake.vulkan.VK_MAX_PHYSICAL_DEVICE_NAME_SIZE
import com.awakekt.awake.vulkan.VK_UUID_SIZE
import com.awakekt.awake.vulkan.VkConstArray
import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkPhysicalDeviceType
import kotlin.jvm.JvmOverloads

/**
 * General properties of a physical device (`VkPhysicalDeviceProperties`).
 *
 * @property apiVersion The highest Vulkan version the device supports, packed as Vulkan expects.
 * @property driverVersion The driver's version, in a vendor-specific encoding.
 * @property vendorID The vendor's PCI identifier.
 * @property deviceID The device's identifier within the vendor.
 * @property deviceType The kind of device: discrete, integrated, virtual, software or other.
 * @property deviceName The device name, as UTF-16 code units of a null-terminated string.
 * @property pipelineCacheUUID A UUID that identifies the device for pipeline cache compatibility.
 * @property limits The device's limits.
 * @property sparseProperties The device's sparse-resource properties.
 */
@VkMutator
class VkPhysicalDeviceProperties @JvmOverloads constructor(
    val apiVersion: Int = 0,
    val driverVersion: Int = 0,
    val vendorID: Int = 0,
    val deviceID: Int = 0,
    val deviceType: VkPhysicalDeviceType = VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_OTHER,
    @VkConstArray("VK_MAX_PHYSICAL_DEVICE_NAME_SIZE")
    val deviceName: CharArray = CharArray(VK_MAX_PHYSICAL_DEVICE_NAME_SIZE),
    @VkConstArray("VK_UUID_SIZE")
    val pipelineCacheUUID: ByteArray = ByteArray(VK_UUID_SIZE),
    val limits: VkPhysicalDeviceLimits = VkPhysicalDeviceLimits(),
    val sparseProperties: VkPhysicalDeviceSparseProperties = VkPhysicalDeviceSparseProperties(),
)
