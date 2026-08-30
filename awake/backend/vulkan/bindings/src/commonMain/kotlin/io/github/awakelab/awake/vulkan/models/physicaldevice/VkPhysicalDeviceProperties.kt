/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models.physicaldevice

import io.github.awakelab.awake.vulkan.VK_MAX_PHYSICAL_DEVICE_NAME_SIZE
import io.github.awakelab.awake.vulkan.VK_UUID_SIZE
import io.github.awakelab.awake.vulkan.VkConstArray
import io.github.awakelab.awake.vulkan.VkMutator
import io.github.awakelab.awake.vulkan.enums.VkPhysicalDeviceType
import kotlin.jvm.JvmOverloads

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
