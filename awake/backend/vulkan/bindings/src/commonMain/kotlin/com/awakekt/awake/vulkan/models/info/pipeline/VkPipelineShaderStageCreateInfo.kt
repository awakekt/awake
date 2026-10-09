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

/**
 * One shader stage of a pipeline (`VkPipelineShaderStageCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of stage creation flags; 0 for none.
 * @property stage The pipeline stage the shader runs in.
 * @property module The raw handle of the shader module that contains the shader.
 * @property pName The name of the shader's entry point.
 * @property pSpecializationInfo Specialization constants for the shader, or `null` for none.
 */
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

/**
 * Values for a shader's specialization constants (`VkSpecializationInfo`).
 *
 * @property mapEntryCount The number of entries in the map.
 * @property pMapEntries Where in the data each constant's value is.
 * @property dataSize The size of the data, in bytes.
 * @property pData The data holding the constants' values.
 */
class VkSpecializationInfo(
    val mapEntryCount: Int = 0,
    val pMapEntries: Array<VkSpecializationMapEntry> = emptyArray(),
    val dataSize: Long = 0,
    val pData: Array<Any>? = null, // Replace Any with the appropriate data type for pData
)

/**
 * Maps one specialization constant to its bytes in the data (`VkSpecializationMapEntry`).
 *
 * @property constantID The `constant_id` the shader declares.
 * @property offset The byte offset of the value in the data.
 * @property size The size of the value, in bytes.
 */
data class VkSpecializationMapEntry(
    val constantID: Int = 0,
    val offset: Int = 0,
    val size: Long = 0,
)
