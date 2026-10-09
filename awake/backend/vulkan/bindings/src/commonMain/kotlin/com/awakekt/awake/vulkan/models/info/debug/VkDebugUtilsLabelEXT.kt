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

/**
 * A coloured label for a region of work, shown in debugging tools (`VkDebugUtilsLabelEXT`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property pLabelName The label's name.
 * @property color The label colour as four floats: red, green, blue and alpha.
 */
@VkMutator
class VkDebugUtilsLabelEXT @JvmOverloads constructor(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEBUG_UTILS_LABEL_EXT,
    val pNext: Any? = null,
    val pLabelName: String? = null,
    @VkConstArray(arraySize = "4")
    val color: FloatArray = FloatArray(4),
)
