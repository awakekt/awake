/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.VkPointer
import com.awakekt.awake.vulkan.enums.VkPipelineCreateFlags
import com.awakekt.awake.vulkan.enums.VkStructureType
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineColorBlendStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineDepthStencilStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineDynamicStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineInputAssemblyStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineMultisampleStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineRasterizationStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineShaderStageCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineTessellationStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineVertexInputStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineViewportStateCreateInfo

/**
 * Parameters for creating a graphics pipeline: its shader stages, fixed-function state, layout and
 * render pass (`VkGraphicsPipelineCreateInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property flags A mask of pipeline creation flags.
 * @property pStages The shader stages of the pipeline.
 * @property pVertexInputState How vertex data is fed to the vertex shader.
 * @property pInputAssemblyState How vertices are assembled into primitives.
 * @property pTessellationState The tessellation state; used only when tessellation shaders are
 * present.
 * @property pViewportState The viewports and scissors.
 * @property pRasterizationState How primitives are rasterized.
 * @property pMultisampleState The multisampling state.
 * @property pDepthStencilState The depth and stencil state; `null` when the subpass has no
 * depth/stencil attachment.
 * @property pColorBlendState How fragment colours are blended into the colour attachments.
 * @property pDynamicState The pipeline state that is set while recording instead of being fixed
 * here.
 * @property layout The raw handle of the pipeline layout.
 * @property renderPass The raw handle of the render pass the pipeline is used with.
 * @property subpass The index of the subpass in which the pipeline is used.
 * @property basePipelineHandle The raw handle of a pipeline to derive from, or 0 for none.
 * @property basePipelineIndex The index into the batch of a pipeline to derive from, or -1 for
 * none.
 */
class VkGraphicsPipelineCreateInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO,
    val pNext: Any? = null,
    val flags: VkPipelineCreateFlags = 0,
    @field:VkArray("stageCount")
    val pStages: Array<VkPipelineShaderStageCreateInfo> = emptyArray(),
    @VkPointer
    val pVertexInputState: Array<VkPipelineVertexInputStateCreateInfo> = arrayOf(
        VkPipelineVertexInputStateCreateInfo(),
    ),
    @VkPointer
    val pInputAssemblyState: Array<VkPipelineInputAssemblyStateCreateInfo> = arrayOf(
        VkPipelineInputAssemblyStateCreateInfo(),
    ),
    @VkPointer
    val pTessellationState: Array<VkPipelineTessellationStateCreateInfo> = arrayOf(
        VkPipelineTessellationStateCreateInfo(),
    ),
    @VkPointer
    val pViewportState: Array<VkPipelineViewportStateCreateInfo> = arrayOf(
        VkPipelineViewportStateCreateInfo(),
    ),
    @VkPointer
    val pRasterizationState: Array<VkPipelineRasterizationStateCreateInfo> = arrayOf(
        VkPipelineRasterizationStateCreateInfo(),
    ),
    @VkPointer
    val pMultisampleState: Array<VkPipelineMultisampleStateCreateInfo> = arrayOf(
        VkPipelineMultisampleStateCreateInfo(),
    ),
    @VkPointer
    val pDepthStencilState: Array<VkPipelineDepthStencilStateCreateInfo>? = null,
    @VkPointer
    val pColorBlendState: Array<VkPipelineColorBlendStateCreateInfo> = arrayOf(
        VkPipelineColorBlendStateCreateInfo(),
    ),
    @VkPointer
    val pDynamicState: Array<VkPipelineDynamicStateCreateInfo> = arrayOf(
        VkPipelineDynamicStateCreateInfo(),
    ),
    @field:VkHandleRef("VkPipelineLayout")
    val layout: VkHandle = 0, // VkPipelineLayout handle
    @field:VkHandleRef("VkRenderPass")
    val renderPass: VkHandle = 0, // VkRenderPass handle
    val subpass: Int = 0,
    @field:VkHandleRef("VkPipeline")
    val basePipelineHandle: VkHandle = 0, // VkPipeline handle
    val basePipelineIndex: Int = -1,
)
