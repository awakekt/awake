/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info.pipeline

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkFlags
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.enums.VkVertexInputRate

/**
 * How fast a vertex buffer binding advances and how large each element is
 * (`VkVertexInputBindingDescription`).
 *
 * @property binding The binding number.
 * @property stride The distance in bytes between consecutive elements.
 * @property inputRate Whether the binding advances per vertex or per instance.
 */
data class VkVertexInputBindingDescription(
    val binding: Int,
    val stride: Int,
    val inputRate: VkVertexInputRate,
)

/**
 * One vertex attribute: where it comes from and how it is read
 * (`VkVertexInputAttributeDescription`).
 *
 * @property location The shader input location of the attribute.
 * @property binding The vertex buffer binding the attribute is read from.
 * @property format The format of the attribute data.
 * @property offset The byte offset of the attribute within an element.
 */
data class VkVertexInputAttributeDescription(
    val location: Int,
    val binding: Int,
    val format: VkFormat,
    val offset: Int,
)

/**
 * How vertex data is read from buffers (`VkPipelineVertexInputStateCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags Reserved for future use; 0.
 * @property pVertexBindingDescriptions The vertex buffer bindings.
 * @property pVertexAttributeDescriptions The vertex attributes.
 */
class VkPipelineVertexInputStateCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineVertexInputStateCreateFlags = 0,
    @VkArray(sizeAlias = "vertexBindingDescriptionCount")
    val pVertexBindingDescriptions: Array<VkVertexInputBindingDescription>? = null,
    @VkArray(sizeAlias = "vertexAttributeDescriptionCount")
    val pVertexAttributeDescriptions: Array<VkVertexInputAttributeDescription>? = null,
)

typealias VkPipelineVertexInputStateCreateFlags = VkFlags
