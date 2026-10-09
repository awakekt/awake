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

/**
 * Parameters for creating a debug utils messenger (`VkDebugUtilsMessengerCreateInfoEXT`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property messageSeverity A mask of the severities the callback is called for; warnings and
 * errors by default.
 * @property messageType A mask of the message types the callback is called for; all types by
 * default.
 * @property pfnUserCallback The function called for each matching message; it returns `true` to
 * abort the call that caused it.
 * @property pUserData A value passed back to the callback unchanged.
 */
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

/**
 * Builds a messenger callback that formats each message and passes the severity label and the
 * formatted text to the given [LogCallback]; the message types, id name and id number stay in the
 * text.
 *
 * The callback never aborts the call that caused the message: it always returns `false`. Pass the
 * result as `pfnUserCallback`.
 */
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
