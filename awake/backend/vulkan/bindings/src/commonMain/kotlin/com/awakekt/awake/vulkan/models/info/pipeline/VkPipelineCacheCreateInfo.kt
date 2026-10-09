/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for creating a pipeline cache (`VkPipelineCacheCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of pipeline cache creation flags; 0 for none.
 * @property pInitialData Data from an earlier cache to start with, or `null`; not supported on iOS.
 */
class VkPipelineCacheCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: Int = 0,
    @field:VkArray("initialDataSize")
    val pInitialData: Array<Any>? = null,
)
