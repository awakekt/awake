/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.Version
import com.awakekt.awake.vulkan.Version.Companion.vkVersion
import com.awakekt.awake.vulkan.enums.VkStructureType

data class VkApplicationInfo(
    var sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_APPLICATION_INFO,
    var pNext: Any? = null,
    var pApplicationName: String,
    var applicationVersion: Int = Version(1, 0, 0).vkVersion,
    var pEngineName: String,
    var engineVersion: Int = Version(1, 0, 0).vkVersion,
    var apiVersion: Int = Version(1, 0, 0).vkVersion,
)
