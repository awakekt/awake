/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkPipelineShaderStageCreateFlags
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkPipelineShaderStageCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineShaderStageCreateFlags = 0,
    val stage: VkShaderStageFlagBits = VkShaderStageFlagBits.VERTEX,
    @field:VkHandleRef("VkShaderModule")
    val module: VkHandle = 0, // VkShaderModule
    val pName: String? = null,
    val pSpecializationInfo: Array<VkSpecializationInfo>? = null,
)

class VkSpecializationInfo(
    val mapEntryCount: Int = 0,
    val pMapEntries: Array<VkSpecializationMapEntry> = emptyArray(),
    val dataSize: Long = 0,
    val pData: Array<Any>? = null, // Replace Any with the appropriate data type for pData
)

data class VkSpecializationMapEntry(
    val constantID: Int = 0,
    val offset: Int = 0,
    val size: Long = 0,
)
