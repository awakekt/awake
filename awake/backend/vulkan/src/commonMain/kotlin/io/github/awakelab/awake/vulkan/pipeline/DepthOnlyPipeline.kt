/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.pipeline

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.enums.VkCullModeFlagBits
import io.github.awakelab.awake.vulkan.enums.VkDynamicState
import io.github.awakelab.awake.vulkan.enums.VkPipelineBindPoint
import io.github.awakelab.awake.vulkan.enums.VkPrimitiveTopology
import io.github.awakelab.awake.vulkan.enums.VkShaderStageFlagBits
import io.github.awakelab.awake.vulkan.enums.VkVertexInputRate
import io.github.awakelab.awake.vulkan.handles.DescriptorSetLayoutHandle
import io.github.awakelab.awake.vulkan.models.VkExtent2D
import io.github.awakelab.awake.vulkan.models.VkOffset2D
import io.github.awakelab.awake.vulkan.models.VkRect2D
import io.github.awakelab.awake.vulkan.models.VkViewport
import io.github.awakelab.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineCacheCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineColorBlendStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineDepthStencilStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineDynamicStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineInputAssemblyStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineLayoutCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineMultisampleStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineRasterizationStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineShaderStageCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineVertexInputStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineViewportStateCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkVertexInputAttributeDescription
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkVertexInputBindingDescription

/**
 * A colorless twin of [RenderPipeline], for rendering into a [DepthTarget]: no fragment output,
 * no color attachment and no color-blend state, because that target's render pass has depth
 * only.
 *
 * Takes the caller's [descriptorSetLayout] rather than declaring one, so its pipeline layout is
 * binding-compatible with whatever descriptor set the caller already writes -- a depth-only
 * vertex shader can then read a transform straight out of the main pass's own uniform buffer
 * instead of needing a second per-draw scheme. Takes the caller's [vertexFormat] for the same
 * reason: it draws the same meshes without a second vertex buffer.
 *
 * `cullMode = NONE`, not front-face culling. Culling either winding is only safe when a mesh's
 * winding is guaranteed outward-consistent per face, and this repo's are not (see
 * [RenderPipeline]'s own rasterization-state comment) -- culling would punch holes in the
 * rendered geometry. A consumer that needs depth-bias-style mitigation applies it in its own
 * shader.
 */
class DepthOnlyPipeline(
    graphicsDevice: GraphicsDevice,
    renderPass: Long,
    descriptorSetLayout: DescriptorSetLayoutHandle,
    shaders: ShaderPair,
    vertexFormat: VertexFormat,
    targetSize: Int,
    vertexEntryPoint: String = "vertexMain",
    fragmentEntryPoint: String = "fragmentMain",
) {
    private val device = graphicsDevice.device

    var pipelineLayout: Long = 0
        private set
    private var pipelineCache: Long = 0
    private var graphicsPipeline: LongArray = longArrayOf()

    // See RenderPipeline's own init doc comment -- same partial-creation-leak guard.
    init {
        try {
            val fragShaderModule = createShaderModule(device, shaders.fragment.toShaderIntArray())
            val vertShaderModule = createShaderModule(device, shaders.vertex.toShaderIntArray())

            val shaderStages = arrayOf(
                VkPipelineShaderStageCreateInfo(
                    stage = VkShaderStageFlagBits.FRAGMENT,
                    module = fragShaderModule,
                    pName = fragmentEntryPoint,
                ),
                VkPipelineShaderStageCreateInfo(
                    stage = VkShaderStageFlagBits.VERTEX,
                    module = vertShaderModule,
                    pName = vertexEntryPoint,
                ),
            )

            val vertexInputInfo = arrayOf(
                VkPipelineVertexInputStateCreateInfo(
                    pVertexBindingDescriptions = arrayOf(
                        VkVertexInputBindingDescription(
                            binding = 0,
                            stride = vertexFormat.strideBytes,
                            inputRate = VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX,
                        ),
                    ),
                    pVertexAttributeDescriptions = vertexFormat.entries.map { entry ->
                        VkVertexInputAttributeDescription(
                            location = entry.attribute.location,
                            binding = 0,
                            format = entry.attribute.format.toVkFormat(),
                            offset = entry.offsetBytes,
                        )
                    }.toTypedArray(),
                ),
            )

            val dynamicInfo = arrayOf(
                VkPipelineDynamicStateCreateInfo(
                    pDynamicStates = arrayOf(VkDynamicState.VK_DYNAMIC_STATE_VIEWPORT, VkDynamicState.VK_DYNAMIC_STATE_SCISSOR),
                ),
            )

            val viewportInfo = arrayOf(
                VkPipelineViewportStateCreateInfo(
                    pViewports = arrayOf(VkViewport(width = targetSize.toFloat(), height = targetSize.toFloat())),
                    pScissors = arrayOf(VkRect2D(offset = VkOffset2D(), extent = VkExtent2D(targetSize, targetSize))),
                ),
            )

            val depthStencil = arrayOf(VkPipelineDepthStencilStateCreateInfo())
            val multisamplingInfo = arrayOf(VkPipelineMultisampleStateCreateInfo())
            val inputAssemblyInfo = arrayOf(
                VkPipelineInputAssemblyStateCreateInfo(
                    topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                    primitiveRestartEnable = false,
                ),
            )
            val rasterizationInfo = arrayOf(
                VkPipelineRasterizationStateCreateInfo(
                    cullMode = VkCullModeFlagBits.VK_CULL_MODE_NONE.value,
                    lineWidth = 1f,
                ),
            )
            // No color attachments in DepthTarget's render pass, so no blend attachments either.
            val colorBlendInfo = arrayOf(VkPipelineColorBlendStateCreateInfo(pAttachments = arrayOf()))

            pipelineLayout = Vulkan.vkCreatePipelineLayout(
                device,
                VkPipelineLayoutCreateInfo(pSetLayouts = arrayOf(descriptorSetLayout.handle)),
            )

            val createInfos = arrayOf(
                VkGraphicsPipelineCreateInfo(
                    pStages = shaderStages,
                    pVertexInputState = vertexInputInfo,
                    pInputAssemblyState = inputAssemblyInfo,
                    pViewportState = viewportInfo,
                    pRasterizationState = rasterizationInfo,
                    pMultisampleState = multisamplingInfo,
                    pColorBlendState = colorBlendInfo,
                    pDepthStencilState = depthStencil,
                    pDynamicState = dynamicInfo,
                    layout = pipelineLayout,
                    renderPass = renderPass,
                    subpass = 0,
                    basePipelineHandle = 0,
                    basePipelineIndex = -1,
                ),
            )
            pipelineCache = Vulkan.vkCreatePipelineCache(device, VkPipelineCacheCreateInfo())
            graphicsPipeline = Vulkan.vkCreateGraphicsPipelines(device, pipelineCache, createInfos)

            Vulkan.vkDestroyShaderModule(device, fragShaderModule)
            Vulkan.vkDestroyShaderModule(device, vertShaderModule)
        } catch (e: Throwable) {
            destroy()
            throw e
        }
    }

    fun bind(commandBuffer: Long) {
        Vulkan.vkCmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline[0])
    }

    fun destroy() {
        graphicsPipeline.forEach { Vulkan.vkDestroyPipeline(device, it) }
        Vulkan.vkDestroyPipelineLayout(device, pipelineLayout)
        Vulkan.vkDestroyPipelineCache(device, pipelineCache)
    }
}
