/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.physicaldevice

/**
 * A physical device handle paired with the instance it was enumerated from.
 *
 * @property physicalDevice The raw `VkPhysicalDevice` handle.
 * @property instance The raw handle of the instance that enumerated the device.
 */
data class VkPhysicalDevice(val physicalDevice: Long, val instance: Long)
