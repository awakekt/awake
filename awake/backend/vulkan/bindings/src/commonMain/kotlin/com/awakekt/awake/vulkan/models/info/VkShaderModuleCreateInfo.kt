/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkShaderModuleCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkShaderModuleCreateFlags = 0,
    @VkArray(sizeAlias = "codeSize", stride = UInt::class)
    val pCode: IntArray,
)

typealias VkShaderModuleCreateFlags = VkFlags
