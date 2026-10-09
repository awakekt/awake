/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkConstArray
import com.awakekt.awake.vulkan.VkMutator
import kotlin.jvm.JvmOverloads

/**
 * The name and specification version of an instance or device extension (`VkExtensionProperties`).
 *
 * @property extensionName The extension's name, such as `VK_KHR_swapchain`.
 * @property specVersion The revision of the extension's specification that the implementation
 * supports.
 */
@VkMutator
data class VkExtensionProperties @JvmOverloads constructor(
    @VkConstArray("VK_MAX_EXTENSION_NAME_SIZE")
    val extensionName: String = "",
    val specVersion: Int = 0,
)
