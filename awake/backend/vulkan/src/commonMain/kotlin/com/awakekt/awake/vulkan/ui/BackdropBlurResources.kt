/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.ui

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.DualKawaseBlur
import com.awakekt.awake.render.passes2d.BackdropBlurPass
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.vulkan.VK_SUBPASS_EXTERNAL
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkAttachmentLoadOp
import com.awakekt.awake.vulkan.enums.VkAttachmentStoreOp
import com.awakekt.awake.vulkan.enums.VkColorComponentFlagBits
import com.awakekt.awake.vulkan.enums.VkCompareOp
import com.awakekt.awake.vulkan.enums.VkCullModeFlagBits
import com.awakekt.awake.vulkan.enums.VkDynamicState
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkFrontFace
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageLayout
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.enums.VkPolygonMode
import com.awakekt.awake.vulkan.enums.VkPrimitiveTopology
import com.awakekt.awake.vulkan.enums.VkSampleCountFlagBits
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.enums.VkVertexInputRate
import com.awakekt.awake.vulkan.enums.flags.VkAccessFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.VkAttachmentDescription
import com.awakekt.awake.vulkan.models.VkAttachmentReference
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkSubpassDependency
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkDescriptorImageInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolSize
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import com.awakekt.awake.vulkan.models.info.VkFilter
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerAddressMode
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerMipmapMode
import com.awakekt.awake.vulkan.models.info.VkSubpassDescription
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
import com.awakekt.awake.vulkan.pipeline.createShaderModule
import com.awakekt.awake.vulkan.pipeline.toShaderIntArray
import com.awakekt.awake.vulkan.pipeline.toVkFormat

/**
 * Everything a backdrop blur needs on Vulkan beyond the UI pass itself: the reduced-resolution
 * levels it renders into, the render pass that writes one, the dual-filter pipeline, and a linear
 * clamp-to-edge sampler.
 *
 * Level 0 is never allocated here: it is the target the UI is drawn into, which the first
 * downsample samples directly. Levels are sized for one backdrop size at a time and rebuilt, after
 * the device is idle, when it changes; a frame that blurs nothing touches none of this.
 */
internal class BackdropBlurResources(
    private val graphicsDevice: GraphicsDevice,
    private val format: VkFormat,
    shaders: ShaderPair,
    private val framesInFlight: Int,
) {
    private val device get() = graphicsDevice.device

    private class Level(val image: Long, val memory: Long, val view: Long, val framebuffer: Long, val width: Int, val height: Int)

    private class DescriptorSlot(val pool: Long, val set: Long)

    /** The render pass a blur pass draws one level with: its result is left ready to sample. */
    val levelRenderPass: Long = createLevelRenderPass()

    /** Linear and clamp-to-edge, so a tap between texels blends them and none wraps around. */
    val sampler: Long = VulkanImages.vkCreateSampler(
        device,
        VkSamplerCreateInfo(
            magFilter = VkFilter.VK_FILTER_LINEAR,
            minFilter = VkFilter.VK_FILTER_LINEAR,
            addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            mipmapMode = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST,
        ),
    )

    private val descriptorSetLayout = VulkanDescriptors.vkCreateDescriptorSetLayout(
        device,
        VkDescriptorSetLayoutCreateInfo(
            pBindings = arrayOf(
                VkDescriptorSetLayoutBinding(
                    binding = 0,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                    stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                ),
                VkDescriptorSetLayoutBinding(
                    binding = 1,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
                    stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                ),
            ),
        ),
    )
    private val pipelineLayout = Vulkan.vkCreatePipelineLayout(
        device,
        VkPipelineLayoutCreateInfo(pSetLayouts = arrayOf(descriptorSetLayout), pPushConstantRanges = arrayOf()),
    )
    private val pipelineCache = Vulkan.vkCreatePipelineCache(device, VkPipelineCacheCreateInfo())
    private val pipeline: Long = createPipeline(shaders)
    private val descriptorSlots = List(framesInFlight) { mutableListOf<DescriptorSlot>() }

    private var levels: Array<Level?> = emptyArray()
    private var levelsWidth = 0
    private var levelsHeight = 0

    /**
     * Makes sure every level a blur of a [width] by [height] backdrop can reach exists and is at
     * least that size. Levels only grow: the shaders address them in texels and read their size
     * from the texture, so a larger level serves a smaller backdrop, and a window and an offscreen
     * layer of different sizes do not rebuild them every frame. Growing waits for the device to go
     * idle.
     */
    fun ensureLevels(width: Int, height: Int) {
        if (fits(width, height)) return
        val grownWidth = maxOf(width, levelsWidth)
        val grownHeight = maxOf(height, levelsHeight)
        if (levels.isNotEmpty()) {
            VulkanBuffers.vkDeviceWaitIdle(device)
            destroyLevels()
        }
        levels = arrayOfNulls(MAX_LEVEL + 1)
        for (level in 1..MAX_LEVEL) {
            levels[level] = createLevel(DualKawaseBlur.levelSize(grownWidth, level), DualKawaseBlur.levelSize(grownHeight, level))
        }
        levelsWidth = grownWidth
        levelsHeight = grownHeight
    }

    /** Whether the levels can hold a blur of a [width] by [height] backdrop. */
    fun fits(width: Int, height: Int): Boolean = levels.isNotEmpty() && width <= levelsWidth && height <= levelsHeight

    /** The view of [level], from 1, for a composite to sample. */
    fun levelView(level: Int): Long = requireNotNull(levels[level]).view

    /**
     * Records [pass] of a blur, outside any render pass: draws [mesh]'s triangle into its target
     * level, scissored to the texels it writes, sampling [sourceView]. [slot] names this pass's
     * descriptor set within frame [frameIndex], so passes recorded together never share one.
     */
    @Suppress("LongParameterList")
    fun recordPass(
        commandBuffer: Long,
        frameIndex: Int,
        slot: Int,
        pass: BackdropBlurPass,
        sourceView: Long,
        mesh: DynamicMesh,
        stats: RenderStatsCounter,
    ) {
        val target = requireNotNull(levels[pass.targetLevel])
        val rect = pass.target
        val scissor = VkRect2D(offset = VkOffset2D(rect.left, rect.top), extent = VkExtent2D(rect.width, rect.height))
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(renderPass = levelRenderPass, framebuffer = target.framebuffer, renderArea = scissor),
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(VkViewport(width = target.width.toFloat(), height = target.height.toFloat())))
        Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(scissor))
        Vulkan.vkCmdBindPipeline(commandBuffer, VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline)
        val set = descriptorSlot(frameIndex, slot).set
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            set,
            0,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
            VkDescriptorImageInfo(sampler = 0, imageView = sourceView),
        )
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            set,
            1,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
            VkDescriptorImageInfo(sampler = sampler, imageView = 0),
        )
        VulkanDescriptors.vkCmdBindDescriptorSet(commandBuffer, pipelineLayout, 0, set)
        mesh.bind(frameIndex, commandBuffer)
        mesh.draw(frameIndex, commandBuffer, stats)
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    }

    /** The view a pass reads: [targetView] for level 0, otherwise that level's own. */
    fun sourceView(level: Int, targetView: Long): Long = if (level == 0) targetView else levelView(level)

    private fun descriptorSlot(frameIndex: Int, slot: Int): DescriptorSlot {
        val slots = descriptorSlots[frameIndex]
        while (slots.size <= slot) {
            val pool = VulkanDescriptors.vkCreateDescriptorPool(
                device,
                VkDescriptorPoolCreateInfo(
                    maxSets = 1,
                    pPoolSizes = arrayOf(
                        VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, descriptorCount = 1),
                        VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER, descriptorCount = 1),
                    ),
                ),
            )
            slots += DescriptorSlot(pool, VulkanDescriptors.vkAllocateDescriptorSet(device, pool, descriptorSetLayout))
        }
        return slots[slot]
    }

    private fun createLevel(width: Int, height: Int): Level {
        val image = VulkanImages.vkCreateImage(
            device,
            VkImageCreateInfo(
                width = width,
                height = height,
                format = format.value,
                usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VkImageUsageFlagBits2.VK_IMAGE_USAGE_SAMPLED_BIT,
            ),
        )
        val requirements = VulkanImages.vkGetImageMemoryRequirements(device, image)
        val memory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(
                allocationSize = requirements.size,
                memoryTypeIndex = VulkanBuffers.findMemoryType(
                    graphicsDevice.physicalDevice,
                    requirements.memoryTypeBits,
                    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                ),
            ),
        )
        VulkanImages.vkBindImageMemory(device, image, memory, 0)
        val view = Vulkan.vkCreateImageView(
            device,
            VkImageViewCreateInfo(
                image = image,
                viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
                format = format,
                subresourceRange = VkImageSubresourceRange(
                    aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
                    baseMipLevel = 0,
                    levelCount = 1,
                    baseArrayLayer = 0,
                    layerCount = 1,
                ),
            ),
        )
        val framebuffer = Vulkan.vkCreateFramebuffer(
            device,
            VkFramebufferCreateInfo(renderPass = levelRenderPass, pAttachments = arrayOf(view), width = width, height = height, layers = 1),
        )
        return Level(image, memory, view, framebuffer, width, height)
    }

    /**
     * Initial layout undefined: a level holds nothing a later pass needs once the next blur
     * starts, and only the scissored texels are written and read. The first dependency waits for
     * every earlier read of the level (the previous blur's, or a composite's) before writing it; the
     * second makes the write visible to the sampling that follows.
     */
    private fun createLevelRenderPass(): Long = Vulkan.vkCreateRenderPass(
        device,
        VkRenderPassCreateInfo(
            pAttachments = arrayOf(
                VkAttachmentDescription(
                    format = format,
                    loadOp = VkAttachmentLoadOp.DONT_CARE,
                    storeOp = VkAttachmentStoreOp.STORE,
                    initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
                    finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                ),
            ),
            pSubpasses = arrayOf(
                VkSubpassDescription(
                    pipelineBindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pColorAttachments = arrayOf(
                        VkAttachmentReference(attachment = 0, layout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL),
                    ),
                ),
            ),
            pDependencies = arrayOf(
                VkSubpassDependency(
                    srcSubpass = VK_SUBPASS_EXTERNAL,
                    dstSubpass = 0,
                    srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value or
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                    srcAccessMask = 0,
                    dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                    dstAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
                ),
                VkSubpassDependency(
                    srcSubpass = 0,
                    dstSubpass = VK_SUBPASS_EXTERNAL,
                    srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                    srcAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
                    dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value,
                    dstAccessMask = VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value,
                ),
            ),
        ),
    )

    @Suppress("LongMethod")
    private fun createPipeline(shaders: ShaderPair): Long {
        val fragmentModule = createShaderModule(device, shaders.fragment.toShaderIntArray())
        val vertexModule = createShaderModule(device, shaders.vertex.toShaderIntArray())
        try {
            val format = VertexFormats2D.BackdropBlurPass
            val info = VkGraphicsPipelineCreateInfo(
                pStages = arrayOf(
                    VkPipelineShaderStageCreateInfo(stage = VkShaderStageFlagBits.FRAGMENT, module = fragmentModule, pName = "fragmentMain"),
                    VkPipelineShaderStageCreateInfo(stage = VkShaderStageFlagBits.VERTEX, module = vertexModule, pName = "vertexMain"),
                ),
                pVertexInputState = arrayOf(
                    VkPipelineVertexInputStateCreateInfo(
                        pVertexBindingDescriptions = arrayOf(
                            VkVertexInputBindingDescription(
                                binding = 0,
                                stride = format.strideBytes,
                                inputRate = VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX,
                            ),
                        ),
                        pVertexAttributeDescriptions = format.entries.map { entry ->
                            VkVertexInputAttributeDescription(
                                location = entry.attribute.location,
                                binding = 0,
                                format = entry.attribute.format.toVkFormat(),
                                offset = entry.offsetBytes,
                            )
                        }.toTypedArray(),
                    ),
                ),
                pInputAssemblyState = arrayOf(
                    VkPipelineInputAssemblyStateCreateInfo(
                        topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                        primitiveRestartEnable = false,
                    ),
                ),
                pViewportState = arrayOf(
                    VkPipelineViewportStateCreateInfo(
                        pViewports = arrayOf(VkViewport(width = 1f, height = 1f)),
                        pScissors = arrayOf(VkRect2D(extent = VkExtent2D(1, 1))),
                    ),
                ),
                pRasterizationState = arrayOf(
                    VkPipelineRasterizationStateCreateInfo(
                        depthClampEnable = false,
                        rasterizerDiscardEnable = false,
                        polygonMode = VkPolygonMode.VK_POLYGON_MODE_FILL,
                        lineWidth = 1.0f,
                        cullMode = VkCullModeFlagBits.VK_CULL_MODE_NONE.value,
                        frontFace = VkFrontFace.VK_FRONT_FACE_CLOCKWISE,
                        depthBiasEnable = false,
                    ),
                ),
                pMultisampleState = arrayOf(
                    VkPipelineMultisampleStateCreateInfo(
                        sampleShadingEnable = false,
                        rasterizationSamples = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT,
                    ),
                ),
                pColorBlendState = arrayOf(
                    VkPipelineColorBlendStateCreateInfo(
                        logicOpEnable = false,
                        pAttachments = arrayOf(
                            VkPipelineColorBlendAttachmentState(
                                colorWriteMask = VkColorComponentFlagBits.VK_COLOR_COMPONENT_R_BIT.value or
                                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_G_BIT.value or
                                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_B_BIT.value or
                                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_A_BIT.value,
                                blendEnable = false,
                            ),
                        ),
                    ),
                ),
                pDynamicState = arrayOf(
                    VkPipelineDynamicStateCreateInfo(
                        pDynamicStates = arrayOf(VkDynamicState.VK_DYNAMIC_STATE_VIEWPORT, VkDynamicState.VK_DYNAMIC_STATE_SCISSOR),
                    ),
                ),
                pDepthStencilState = arrayOf(
                    VkPipelineDepthStencilStateCreateInfo(
                        depthTestEnable = false,
                        depthWriteEnable = false,
                        depthCompareOp = VkCompareOp.VK_COMPARE_OP_NEVER,
                        depthBoundsTestEnable = false,
                        stencilTestEnable = false,
                    ),
                ),
                layout = pipelineLayout,
                renderPass = levelRenderPass,
                subpass = 0,
            )
            return Vulkan.vkCreateGraphicsPipelines(device, pipelineCache, arrayOf(info))[0]
        } finally {
            Vulkan.vkDestroyShaderModule(device, fragmentModule)
            Vulkan.vkDestroyShaderModule(device, vertexModule)
        }
    }

    private fun destroyLevels() {
        for (level in levels) {
            if (level == null) continue
            Vulkan.vkDestroyFramebuffer(device, level.framebuffer)
            Vulkan.vkDestroyImageView(device, level.view)
            VulkanImages.vkDestroyImage(device, level.image)
            VulkanBuffers.vkFreeMemory(device, level.memory)
        }
        levels = emptyArray()
        levelsWidth = 0
        levelsHeight = 0
    }

    fun destroy() {
        destroyLevels()
        descriptorSlots.forEach { slots ->
            slots.forEach { VulkanDescriptors.vkDestroyDescriptorPool(device, it.pool) }
            slots.clear()
        }
        Vulkan.vkDestroyPipeline(device, pipeline)
        Vulkan.vkDestroyPipelineLayout(device, pipelineLayout)
        Vulkan.vkDestroyPipelineCache(device, pipelineCache)
        VulkanDescriptors.vkDestroyDescriptorSetLayout(device, descriptorSetLayout)
        VulkanImages.vkDestroySampler(device, sampler)
        Vulkan.vkDestroyRenderPass(device, levelRenderPass)
    }

    companion object {
        /** The deepest level any quality reaches. */
        val MAX_LEVEL: Int = com.awakekt.awake.core.graphics2d.BackdropBlurQuality.entries.maxOf { it.maxIterations }

        /**
         * A render pass that resumes drawing into a target a blur sampled: its colour is loaded,
         * not cleared, from and back to the attachment layout, and a depth attachment, when
         * [depthFormat] is set, is neither kept nor read.
         *
         * Compatible with the pass it continues, so the same pipelines and framebuffer serve both.
         */
        fun createContinueRenderPass(device: Long, colorFormat: VkFormat, depthFormat: VkFormat?): Long {
            val color = VkAttachmentDescription(
                format = colorFormat,
                loadOp = VkAttachmentLoadOp.LOAD,
                storeOp = VkAttachmentStoreOp.STORE,
                initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            )
            val depth = depthFormat?.let {
                VkAttachmentDescription(
                    format = it,
                    loadOp = VkAttachmentLoadOp.DONT_CARE,
                    storeOp = VkAttachmentStoreOp.DONT_CARE,
                    initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
                    finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                )
            }
            return Vulkan.vkCreateRenderPass(
                device,
                VkRenderPassCreateInfo(
                    pAttachments = if (depth == null) arrayOf(color) else arrayOf(color, depth),
                    pSubpasses = arrayOf(
                        VkSubpassDescription(
                            pipelineBindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pColorAttachments = arrayOf(
                                VkAttachmentReference(attachment = 0, layout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL),
                            ),
                            pDepthStencilAttachment = depth?.let {
                                arrayOf(VkAttachmentReference(attachment = 1, layout = VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL))
                            },
                        ),
                    ),
                    // Render pass compatibility covers the dependencies too, so these repeat the
                    // continued pass's own: the UI pass's, or the scene pass's when there is depth.
                    // The barrier that returns the target from sampling orders the blur's reads.
                    pDependencies = arrayOf(if (depth == null) uiPassDependency() else scenePassDependency()),
                ),
            )
        }

        private fun uiPassDependency() = VkSubpassDependency(
            srcSubpass = VK_SUBPASS_EXTERNAL,
            dstSubpass = 0,
            srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
            srcAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
            dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
            dstAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT.value or
                VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
        )

        private fun scenePassDependency() = VkSubpassDependency(
            srcSubpass = VK_SUBPASS_EXTERNAL,
            dstSubpass = 0,
            srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value or
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value,
            srcAccessMask = 0,
            dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value or
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value,
            dstAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT.value or
                VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value or
                VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value,
        )
    }
}
