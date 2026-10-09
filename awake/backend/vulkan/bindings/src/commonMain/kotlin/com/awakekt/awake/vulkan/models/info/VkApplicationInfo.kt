/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.Version
import com.awakekt.awake.vulkan.Version.Companion.vkVersion
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Describes the application and engine to the driver (`VkApplicationInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property pApplicationName The name of the application.
 * @property applicationVersion The application's version, packed as Vulkan expects.
 * @property pEngineName The name of the engine that created the application.
 * @property engineVersion The engine's version, packed as Vulkan expects.
 * @property apiVersion The highest Vulkan version the application expects to use, packed as Vulkan
 * expects.
 */
data class VkApplicationInfo(
    var sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO,
    var pNext: Any? = null,
    var pApplicationName: String,
    var applicationVersion: Int = Version(1, 0, 0).vkVersion,
    var pEngineName: String,
    var engineVersion: Int = Version(1, 0, 0).vkVersion,
    var apiVersion: Int = Version(1, 0, 0).vkVersion,
)
