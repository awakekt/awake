/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.VkPointer
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating a Vulkan instance (`VkInstanceCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of instance creation flags, such as portability enumeration.
 * @property pApplicationInfo Information about the application; the first element is used.
 * @property ppEnabledLayerNames The names of the layers to enable.
 * @property ppEnabledExtensionNames The names of the instance extensions to enable.
 */
class VkInstanceCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkInstanceCreateFlags = 0,
    @VkPointer
    val pApplicationInfo: Array<VkApplicationInfo>? = null,
    @VkArray(sizeAlias = "enabledLayerCount")
    val ppEnabledLayerNames: Array<String>? = null,
    @VkArray(sizeAlias = "enabledExtensionCount")
    val ppEnabledExtensionNames: Array<String>? = null,
)

// Representing VkInstanceCreateFlags as a typealias of Int
typealias VkInstanceCreateFlags = VkFlags
