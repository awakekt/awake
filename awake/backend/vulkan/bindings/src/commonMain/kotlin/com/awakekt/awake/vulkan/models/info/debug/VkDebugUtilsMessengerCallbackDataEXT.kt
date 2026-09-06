/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.debug

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.VkMutator
import com.awakekt.awake.vulkan.enums.VkStructureType
import kotlin.jvm.JvmOverloads

@VkMutator
class VkDebugUtilsMessengerCallbackDataEXT @JvmOverloads constructor(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CALLBACK_DATA_EXT,
    val pNext: Any? = null,
    val flags: VkDebugUtilsMessengerCallbackDataFlagsEXT = 0,
    val pMessageIdName: String? = "",
    val messageIdNumber: Int = 0,
    val pMessage: String = "",
    @VkArray(sizeAlias = "queueLabelCount")
    val pQueueLabels: Array<VkDebugUtilsLabelEXT>? = null,
    @VkArray(sizeAlias = "cmdBufLabelCount")
    val pCmdBufLabels: Array<VkDebugUtilsLabelEXT>? = null,
    @VkArray(sizeAlias = "objectCount")
    val pObjects: Array<VkDebugUtilsObjectNameInfoEXT>? = null,
)

typealias VkDebugUtilsMessengerCallbackDataFlagsEXT = VkFlags
