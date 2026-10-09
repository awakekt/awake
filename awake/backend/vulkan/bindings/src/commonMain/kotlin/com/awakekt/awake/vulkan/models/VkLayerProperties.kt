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
 * The name, versions and description of an instance layer (`VkLayerProperties`).
 *
 * @property layerName The layer's name, such as `VK_LAYER_KHRONOS_validation`.
 * @property specVersion The Vulkan version the layer was written against, packed like an API
 * version.
 * @property implementationVersion The version of the layer implementation, which increases with
 * each revision of it.
 * @property description A human-readable description of the layer.
 */
@VkMutator
data class VkLayerProperties @JvmOverloads constructor(
    @VkConstArray("VK_MAX_EXTENSION_NAME_SIZE")
    val layerName: String = "",
    val specVersion: Int = 0,
    val implementationVersion: Int = 0,
    @VkConstArray("VK_MAX_DESCRIPTION_SIZE")
    val description: String = "",
)
