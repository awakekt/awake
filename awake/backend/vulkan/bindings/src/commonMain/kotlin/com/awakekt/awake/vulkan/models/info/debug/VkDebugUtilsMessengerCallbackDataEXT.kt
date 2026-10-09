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

/**
 * The details of one debug utils message passed to the messenger callback
 * (`VkDebugUtilsMessengerCallbackDataEXT`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property pMessageIdName The identifier of the message, such as a validation VUID, or `null`.
 * @property messageIdNumber A numeric identifier of the message.
 * @property pMessage The message text.
 * @property pQueueLabels The labels of the queue the message relates to.
 * @property pCmdBufLabels The labels of the command buffer the message relates to.
 * @property pObjects The objects the message relates to.
 */
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
