/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.debug

import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.render.passes.debug.DebugLineUniformLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkCullModeFlagBits
import com.awakekt.awake.vulkan.enums.VkDynamicState
import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.enums.VkPrimitiveTopology
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.enums.VkVertexInputRate
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineCacheCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineColorBlendAttachmentState
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineColorBlendStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineDepthStencilStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineDynamicStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineInputAssemblyStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineMultisampleStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineRasterizationStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineShaderStageCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineVertexInputStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineViewportStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkVertexInputAttributeDescription
import com.awakekt.awake.vulkan.models.info.pipeline.VkVertexInputBindingDescription
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.VulkanMaterialBinding
import com.awakekt.awake.vulkan.pipeline.VulkanPipelineHandle
import com.awakekt.awake.vulkan.pipeline.createShaderModule
import com.awakekt.awake.vulkan.pipeline.toShaderIntArray
import com.awakekt.awake.vulkan.pipeline.toVkFormat
import com.awakekt.awake.vulkan.swapchain.SwapchainManager

/**
 * A `LINE_LIST` pipeline for world-space debug lines (e.g. a
 * [com.awakekt.awake.core.math.Frustum] wireframe) -- reuses the existing 3D
 * [com.awakekt.awake.vulkan.pipeline.RenderPipeline]'s already-created
 * [renderPass] (same pattern `UiGlyphRenderPipeline` uses for `UiRenderPipeline`'s render
 * pass), so lines draw within the same render pass/depth attachment as scene geometry --
 * real depth-testing against the cube/ground, not an X-ray overlay. Bound and drawn right
 * after the 3D draw-call loop, before that pass ends (see `Renderer.recordCommandBuffer`).
 */
class LineRenderPipeline(
    graphicsDevice: GraphicsDevice,
    private val swapchainManager: SwapchainManager,
    private val renderPass: Long,
    shaders: ShaderPair,
    private val framesInFlight: Int = 1,
    private val vertexEntryPoint: String = "vertexMain",
    private val fragmentEntryPoint: String = "fragmentMain",
) : VulkanPipelineHandle {
    private val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device

    private var pipelineLayout: Long = 0
    private var pipelineCache: Long = 0
    private var graphicsPipeline: LongArray = longArrayOf()
    private lateinit var uniformSlots: PerFrameUniformSlots
    private val descriptorSetLayout get() = uniformSlots.descriptorSetLayout

    override val pipelineHandle: Long get() = graphicsPipeline[0]
    override val pipelineLayoutHandle: Long get() = pipelineLayout
    override val engineBoundSemantics: Set<BindingSemantic> get() = emptySet()

    /** This frame slot's already-allocated uniform descriptor set, for the shared opaque feature
     * to bind at set 0 -- the same set [bind] binds directly. */
    fun uniformBinding(frameIndex: Int): VulkanMaterialBinding = uniformSlots[frameIndex]

    // See RenderPipeline's own init doc comment -- same partial-creation-leak guard.
    init {
        require(framesInFlight > 0) { "framesInFlight must be positive." }
        try {
            uniformSlots = PerFrameUniformSlots(
                graphicsDevice,
                DebugLineUniformLayout.total * Float.SIZE_BYTES,
                VkShaderStageFlagBits.VERTEX.value,
                framesInFlight,
            )
            createGraphicsPipeline(shaders.vertex, shaders.fragment)
        } catch (e: Throwable) {
            destroy()
            throw e
        }
    }

    /** Writes this frame's view-projection matrix (lines are already in world space, so
     * model is implicitly identity -- mvp == viewProjection). */
    fun writeMvp(mvp: FloatArray) = writeMvp(frameIndex = 0, mvp = mvp)

    fun writeMvp(frameIndex: Int, mvp: FloatArray) = uniformSlots.write(frameIndex, mvp)

    private fun createGraphicsPipeline(vertShaderCode: ByteArray, fragShaderCode: ByteArray) {
        val fragShaderModule = createShaderModule(device, fragShaderCode.toShaderIntArray())
        val vertShaderModule = createShaderModule(device, vertShaderCode.toShaderIntArray())

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
                        stride = DebugLineLayout.Format.strideBytes,
                        inputRate = VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX,
                    ),
                ),
                // Derived from DebugLineLayout, not restated. The locations, formats and
                // offsets were hardcoded here and again in WebGPU's line pipeline, so the same
                // layout was written in three places -- the drift `awake-render-pipeline`'s
                // "never hardcode vertex attribute descriptions" rule names lines for
                // specifically.
                pVertexAttributeDescriptions = DebugLineLayout.Format.entries.map { entry ->
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
                pDynamicStates = arrayOf(
                    VkDynamicState.VK_DYNAMIC_STATE_VIEWPORT,
                    VkDynamicState.VK_DYNAMIC_STATE_SCISSOR,
                ),
            ),
        )

        val viewportInfo = arrayOf(
            VkPipelineViewportStateCreateInfo(
                pViewports = arrayOf(
                    VkViewport(
                        width = swapchainManager.extent.width.toFloat(),
                        height = swapchainManager.extent.height.toFloat(),
                    ),
                ),
                pScissors = arrayOf(VkRect2D(offset = VkOffset2D(), extent = swapchainManager.extent)),
            ),
        )

        val depthStencil = arrayOf(VkPipelineDepthStencilStateCreateInfo())
        val multisamplingInfo = arrayOf(VkPipelineMultisampleStateCreateInfo())

        val inputAssemblyInfo = arrayOf(
            VkPipelineInputAssemblyStateCreateInfo(
                topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_LINE_LIST,
                primitiveRestartEnable = false,
            ),
        )

        val rasterizationInfo = arrayOf(
            VkPipelineRasterizationStateCreateInfo(
                cullMode = VkCullModeFlagBits.VK_CULL_MODE_NONE.value,
                lineWidth = 1f,
            ),
        )

        val blendAttachment = VkPipelineColorBlendAttachmentState(blendEnable = false)
        val colorBlendInfo = arrayOf(VkPipelineColorBlendStateCreateInfo(pAttachments = arrayOf(blendAttachment)))

        pipelineLayout = Vulkan.vkCreatePipelineLayout(
            device,
            VkPipelineLayoutCreateInfo(pSetLayouts = arrayOf(descriptorSetLayout)),
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
    }

    fun bind(commandBuffer: Long) = bind(commandBuffer, frameIndex = 0)

    fun bind(commandBuffer: Long, frameIndex: Int) {
        val slot = uniformSlots[frameIndex]
        Vulkan.vkCmdBindPipeline(
            commandBuffer,
            VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
            graphicsPipeline[0],
        )
        VulkanDescriptors.vkCmdBindDescriptorSet(commandBuffer, pipelineLayout, 0, slot.descriptorSet)
    }

    fun destroy() {
        graphicsPipeline.forEach { Vulkan.vkDestroyPipeline(device, it) }
        Vulkan.vkDestroyPipelineLayout(device, pipelineLayout)
        Vulkan.vkDestroyPipelineCache(device, pipelineCache)
        if (::uniformSlots.isInitialized) uniformSlots.destroy()
    }
}
