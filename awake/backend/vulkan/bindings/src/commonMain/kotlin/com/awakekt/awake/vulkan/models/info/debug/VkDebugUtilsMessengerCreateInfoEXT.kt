/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.debug

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.flags.VkDebugUtilsMessageSeverityFlagBitsEXT
import com.awakekt.awake.vulkan.enums.flags.VkDebugUtilsMessageSeverityFlagsEXT
import com.awakekt.awake.vulkan.enums.flags.VkDebugUtilsMessageTypeFlagBitsEXT
import com.awakekt.awake.vulkan.enums.flags.VkDebugUtilsMessageTypeFlagsEXT
import com.awakekt.awake.vulkan.enums.flags.formatted

typealias PFN_vkDebugUtilsMessengerCallbackEXT = (
    messageSeverity: VkDebugUtilsMessageSeverityFlagBitsEXT,
    messageTypes: VkDebugUtilsMessageTypeFlagsEXT,
    pCallbackData: VkDebugUtilsMessengerCallbackDataEXT,
    pUserData: Any?,
) -> VkBool32

data class VkDebugUtilsMessengerCreateInfoEXT(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT,
    val pNext: Any? = null,
    val flags: VkDebugUtilsMessengerCreateFlagsEXT = 0,
    val messageSeverity: VkDebugUtilsMessageSeverityFlagsEXT =
        VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT.value or
            VkDebugUtilsMessageSeverityFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT.value,
    val messageType: VkDebugUtilsMessageTypeFlagsEXT =
        VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT.value or
            VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT.value or
            VkDebugUtilsMessageTypeFlagBitsEXT.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT.value,
    val pfnUserCallback: PFN_vkDebugUtilsMessengerCallbackEXT = { _, _, _, _ -> false },
    val pUserData: Any? = null,
)

typealias VkDebugUtilsMessengerCreateFlagsEXT = VkFlags

typealias LogCallback = (String, String) -> Unit

val DebugUtilsFormattedCallback: (LogCallback) -> PFN_vkDebugUtilsMessengerCallbackEXT =
    { logCallback ->
        { severity, messageType, callbackData, userData ->
            val severityString = severity.formatted
            val types = messageType.formatted
            val messageIdName = callbackData.pMessageIdName
            val messageIdNumber = callbackData.messageIdNumber
            val message = callbackData.pMessage

            val formattedMessage = message.split(",").joinToString("\n") {
                it.trim().split("|").joinToString("\n") { it.trim() }
            }

            val logMessage = buildString {
                appendLine("$types $severityString:")
                appendLine("[$messageIdName] Code $messageIdNumber:")
                appendLine(formattedMessage)
            }

            logCallback(severityString, logMessage)
            false
        }
    }
