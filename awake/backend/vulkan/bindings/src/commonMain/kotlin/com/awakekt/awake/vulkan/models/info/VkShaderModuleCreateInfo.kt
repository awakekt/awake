/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating a shader module from SPIR-V (`VkShaderModuleCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property pCode The SPIR-V code, as 32-bit words.
 */
class VkShaderModuleCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkShaderModuleCreateFlags = 0,
    @VkArray(sizeAlias = "codeSize", stride = UInt::class)
    val pCode: IntArray,
)

typealias VkShaderModuleCreateFlags = VkFlags
