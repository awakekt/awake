/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.debug

import com.awakekt.awake.vulkan.VkConstArray
import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkStructureType
import kotlin.jvm.JvmOverloads

@VkMutator
class VkDebugUtilsLabelEXT @JvmOverloads constructor(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT,
    val pNext: Any? = null,
    val pLabelName: String? = null,
    @VkConstArray(arraySize = "4")
    val color: FloatArray = FloatArray(4),
)
