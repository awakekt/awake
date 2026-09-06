/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkBool32
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkCullModeFlagBits
import com.awakekt.awake.vulkan.enums.VkCullModeFlags
import com.awakekt.awake.vulkan.enums.VkFrontFace
import com.awakekt.awake.vulkan.enums.VkPolygonMode
import com.awakekt.awake.vulkan.enums.VkStructureType

class VkPipelineRasterizationStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineRasterizationStateCreateFlags = 0,
    val depthClampEnable: VkBool32 = false,
    val rasterizerDiscardEnable: VkBool32 = false,
    val polygonMode: VkPolygonMode = VkPolygonMode.VK_POLYGON_MODE_FILL,
    val cullMode: VkCullModeFlags = VkCullModeFlagBits.VK_CULL_MODE_BACK_BIT.value,
    val frontFace: VkFrontFace = VkFrontFace.VK_FRONT_FACE_CLOCKWISE,
    val depthBiasEnable: VkBool32 = false,
    val depthBiasConstantFactor: Float = 0.0f,
    val depthBiasClamp: Float = 0.0f,
    val depthBiasSlopeFactor: Float = 0.0f,
    val lineWidth: Float = 1.0f,
)

typealias VkPipelineRasterizationStateCreateFlags = VkFlags
