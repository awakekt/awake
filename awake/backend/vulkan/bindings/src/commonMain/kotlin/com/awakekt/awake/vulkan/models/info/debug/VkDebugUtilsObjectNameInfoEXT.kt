/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.debug

import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkObjectType
import com.awakekt.awake.vulkan.enums.VkStructureType
import kotlin.jvm.JvmOverloads

/**
 * Names a Vulkan object for debugging tools and messages (`VkDebugUtilsObjectNameInfoEXT`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property objectType The type of the object.
 * @property objectHandle The raw handle of the object.
 * @property pObjectName The name, or `null` for none.
 */
@VkMutator
data class VkDebugUtilsObjectNameInfoEXT @JvmOverloads constructor(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEBUG_UTILS_OBJECT_NAME_INFO_EXT,
    val pNext: Any? = null,
    val objectType: VkObjectType = VkObjectType.VK_OBJECT_TYPE_UNKNOWN,
    val objectHandle: Long = 0,
    val pObjectName: String? = null,
)
