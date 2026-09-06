/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models

import com.awakekt.awake.vulkan.VkConstArray
import com.awakekt.awake.vulkan.VkMutator
import kotlin.jvm.JvmOverloads

@VkMutator
data class VkLayerProperties @JvmOverloads constructor(
    @VkConstArray("VK_MAX_EXTENSION_NAME_SIZE")
    val layerName: String = "",
    val specVersion: Int = 0,
    val implementationVersion: Int = 0,
    @VkConstArray("VK_MAX_DESCRIPTION_SIZE")
    val description: String = "",
)
