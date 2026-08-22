// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.ui

import io.github.ronjunevaldoz.awake.render.passes2d.UiPipelineKind
import io.github.ronjunevaldoz.awake.vulkan.VK_SUBPASS_EXTERNAL
import io.github.ronjunevaldoz.awake.vulkan.Vulkan
import io.github.ronjunevaldoz.awake.vulkan.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.vulkan.enums.VkAttachmentLoadOp
import io.github.ronjunevaldoz.awake.vulkan.enums.VkBlendFactor
import io.github.ronjunevaldoz.awake.vulkan.enums.VkColorComponentFlagBits
import io.github.ronjunevaldoz.awake.vulkan.enums.VkCullModeFlagBits
import io.github.ronjunevaldoz.awake.vulkan.enums.VkDynamicState
import io.github.ronjunevaldoz.awake.vulkan.enums.VkFormat
import io.github.ronjunevaldoz.awake.vulkan.enums.VkImageLayout
import io.github.ronjunevaldoz.awake.vulkan.enums.VkPipelineBindPoint
import io.github.ronjunevaldoz.awake.vulkan.enums.VkPrimitiveTopology
import io.github.ronjunevaldoz.awake.vulkan.enums.VkShaderStageFlagBits
import io.github.ronjunevaldoz.awake.vulkan.enums.VkVertexInputRate
import io.github.ronjunevaldoz.awake.vulkan.enums.flags.VkAccessFlagBits
import io.github.ronjunevaldoz.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import io.github.ronjunevaldoz.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import io.github.ronjunevaldoz.awake.vulkan.gen.VulkanBuffers
import io.github.ronjunevaldoz.awake.vulkan.gen.VulkanDescriptors
import io.github.ronjunevaldoz.awake.vulkan.models.VkAttachmentDescription
import io.github.ronjunevaldoz.awake.vulkan.models.VkAttachmentReference
import io.github.ronjunevaldoz.awake.vulkan.models.VkOffset2D
import io.github.ronjunevaldoz.awake.vulkan.models.VkRect2D
import io.github.ronjunevaldoz.awake.vulkan.models.VkSubpassDependency
import io.github.ronjunevaldoz.awake.vulkan.models.VkViewport
import io.github.ronjunevaldoz.awake.vulkan.enums.VkCompareOp
import io.github.ronjunevaldoz.awake.vulkan.enums.VkFrontFace
import io.github.ronjunevaldoz.awake.vulkan.enums.VkPolygonMode
import io.github.ronjunevaldoz.awake.vulkan.enums.VkSampleCountFlagBits
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkBufferCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkBufferUsageFlagBits
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkDescriptorBufferInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkDescriptorImageInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkDescriptorPoolSize
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkDescriptorType
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkMemoryAllocateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkRenderPassCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkSubpassDescription
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineCacheCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineColorBlendAttachmentState
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineColorBlendStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineDepthStencilStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineDynamicStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineInputAssemblyStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineLayoutCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineMultisampleStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineRasterizationStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineShaderStageCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineVertexInputStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkPipelineViewportStateCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkVertexInputAttributeDescription
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormats2D
import io.github.ronjunevaldoz.awake.vulkan.models.info.pipeline.VkVertexInputBindingDescription
import io.github.ronjunevaldoz.awake.vulkan.pipeline.createShaderModule
import io.github.ronjunevaldoz.awake.vulkan.pipeline.toShaderIntArray
import io.github.ronjunevaldoz.awake.vulkan.pipeline.toVkFormat
import io.github.ronjunevaldoz.awake.vulkan.swapchain.SwapchainManager
import io.github.ronjunevaldoz.awake.vulkan.texture.Texture


/**
 * Unified UI overlay pipeline -- handles colored quads, rounded quads, font glyphs, and textures.
 */
class UiRenderPipeline(
    graphicsDevice: GraphicsDevice,
    private val swapchainManager: SwapchainManager,
    vertShaderCode: ByteArray,
    fragShaderCode: ByteArray,
    val kind: UiPipelineKind = UiPipelineKind.Quad,
    val hasTexture: Boolean = false,
    externalRenderPass: Long? = null,
    private val framesInFlight: Int = 1,
    fixedTexture: Texture? = null,
) {
    private val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    var renderPass: Long = 0
        private set
    private var descriptorSetLayout: Long = 0
    private var descriptorPool: Long = 0
    private var descriptorSet: Long = 0
    private var screenSizeBuffer: Long = 0
    private var screenSizeBufferMemory: Long = 0
    private var pipelineLayout: Long = 0
    private var pipelineCache: Long = 0
    private var graphicsPipeline: LongArray = longArrayOf()

    private val descriptorSlotsByFrame = List(framesInFlight) {
        mutableListOf<TextureDescriptorSlot>()
    }

    private data class TextureDescriptorSlot(
        val descriptorPool: Long,
        val descriptorSet: Long,
    )

    private var ownsRenderPass: Boolean = externalRenderPass == null

    init {
        require(framesInFlight > 0) { "framesInFlight must be positive." }
        try {
            renderPass = externalRenderPass ?: createRenderPass()
            descriptorSetLayout = createDescriptorSetLayout()
            createScreenSizeUniformBuffer()
            if (!hasTexture || fixedTexture != null) {
                createFixedDescriptorSet(fixedTexture)
            }
            createGraphicsPipeline(vertShaderCode, fragShaderCode)
            writeScreenSize(swapchainManager.extent.width.toFloat(), swapchainManager.extent.height.toFloat())
        } catch (e: Throwable) {
            destroy()
            throw e
        }
    }

    private fun createRenderPass(): Long = Vulkan.vkCreateRenderPass(
        device,
        VkRenderPassCreateInfo(
            pAttachments = arrayOf(
                VkAttachmentDescription(
                    format = swapchainManager.imageFormat,
                    loadOp = VkAttachmentLoadOp.LOAD,
                    initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                ),
            ),
            pSubpasses = arrayOf(
                VkSubpassDescription(
                    pipelineBindPoint = VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pColorAttachments = arrayOf(
                        VkAttachmentReference(
                            attachment = 0,
                            layout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        ),
                    ),
                ),
            ),
            pDependencies = arrayOf(
                VkSubpassDependency(
                    srcSubpass = VK_SUBPASS_EXTERNAL,
                    dstSubpass = 0,
                    srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                    srcAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
                    dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                    dstAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT.value or
                        VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
                ),
            ),
        ),
    )

    private fun createDescriptorSetLayout(): Long {
        val screenSizeStageFlags = VkShaderStageFlagBits.VERTEX.value or VkShaderStageFlagBits.FRAGMENT.value
        val bindings = if (hasTexture) {
            arrayOf(
                VkDescriptorSetLayoutBinding(
                    binding = 0,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    stageFlags = screenSizeStageFlags,
                ),
                VkDescriptorSetLayoutBinding(
                    binding = 1,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                ),
            )
        } else {
            arrayOf(
                VkDescriptorSetLayoutBinding(
                    binding = 0,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    stageFlags = screenSizeStageFlags,
                ),
            )
        }
        return VulkanDescriptors.vkCreateDescriptorSetLayout(
            device,
            VkDescriptorSetLayoutCreateInfo(pBindings = bindings),
        )
    }

    private fun createScreenSizeUniformBuffer() {
        val bufferSize = SCREEN_SIZE_UNIFORM_BYTES.toLong()
        screenSizeBuffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(size = bufferSize, usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, screenSizeBuffer)
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        screenSizeBufferMemory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
        )
        VulkanBuffers.vkBindBufferMemory(device, screenSizeBuffer, screenSizeBufferMemory, 0)
    }

    private fun createFixedDescriptorSet(fixedTexture: Texture?) {
        val poolSizes = if (fixedTexture != null) {
            arrayOf(
                VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount = 1),
                VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, descriptorCount = 1),
            )
        } else {
            arrayOf(
                VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount = 1),
            )
        }
        descriptorPool = VulkanDescriptors.vkCreateDescriptorPool(
            device,
            VkDescriptorPoolCreateInfo(maxSets = 1, pPoolSizes = poolSizes),
        )
        descriptorSet = VulkanDescriptors.vkAllocateDescriptorSet(device, descriptorPool, descriptorSetLayout)
        VulkanDescriptors.vkUpdateDescriptorSetBuffer(
            device,
            descriptorSet,
            0,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
            VkDescriptorBufferInfo(buffer = screenSizeBuffer, range = SCREEN_SIZE_UNIFORM_BYTES.toLong()),
        )
        if (fixedTexture != null) {
            VulkanDescriptors.vkUpdateDescriptorSetImage(
                device,
                descriptorSet,
                1,
                VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                VkDescriptorImageInfo(
                    sampler = fixedTexture.sampler.handle,
                    imageView = fixedTexture.imageView.handle,
                ),
            )
        }
    }

    private fun descriptorSlot(frameIndex: Int, drawSlotIndex: Int): TextureDescriptorSlot {
        val slots = descriptorSlotsByFrame[frameIndex]
        while (slots.size <= drawSlotIndex) {
            slots.add(createDescriptorSlot())
        }
        return slots[drawSlotIndex]
    }

    private fun createDescriptorSlot(): TextureDescriptorSlot {
        val pool = VulkanDescriptors.vkCreateDescriptorPool(
            device,
            VkDescriptorPoolCreateInfo(
                maxSets = 1,
                pPoolSizes = arrayOf(
                    VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount = 1),
                    VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, descriptorCount = 1),
                ),
            ),
        )
        val set = VulkanDescriptors.vkAllocateDescriptorSet(device, pool, descriptorSetLayout)
        VulkanDescriptors.vkUpdateDescriptorSetBuffer(
            device,
            set,
            0,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
            VkDescriptorBufferInfo(buffer = screenSizeBuffer, range = SCREEN_SIZE_UNIFORM_BYTES.toLong()),
        )
        return TextureDescriptorSlot(pool, set)
    }

    fun bindMaterial(commandBuffer: Long, frameIndex: Int, drawSlotIndex: Int, sampler: Long, imageView: Long) {
        val slot = descriptorSlot(frameIndex, drawSlotIndex)
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            slot.descriptorSet,
            1,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
            VkDescriptorImageInfo(
                sampler = sampler,
                imageView = imageView,
            ),
        )
        Vulkan.vkCmdBindPipeline(
            commandBuffer,
            VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
            graphicsPipeline[0],
        )
        VulkanDescriptors.vkCmdBindDescriptorSet(commandBuffer, pipelineLayout, 0, slot.descriptorSet)
    }

    fun bindMaterial(commandBuffer: Long, sampler: Long, imageView: Long) =
        bindMaterial(commandBuffer, frameIndex = 0, drawSlotIndex = 0, sampler = sampler, imageView = imageView)

    fun prepareDescriptorSet(texture: Texture, frameIndex: Int = 0): Long {
        val slot = descriptorSlot(frameIndex, 0)
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            slot.descriptorSet,
            1,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
            VkDescriptorImageInfo(
                sampler = texture.sampler.handle,
                imageView = texture.imageView.handle,
            ),
        )
        return slot.descriptorSet
    }

    fun writeScreenSize(width: Float, height: Float) {
        VulkanBuffers.writeBufferMemoryFloats(
            device,
            screenSizeBufferMemory,
            0,
            floatArrayOf(width, height, 0f, 0f),
        )
    }

    private fun createGraphicsPipeline(vertShaderCode: ByteArray, fragShaderCode: ByteArray) {
        val fragShaderModule = createShaderModule(device, fragShaderCode.toShaderIntArray())
        val vertShaderModule = createShaderModule(device, vertShaderCode.toShaderIntArray())

        val shaderStages = arrayOf(
            VkPipelineShaderStageCreateInfo(stage = VkShaderStageFlagBits.FRAGMENT, module = fragShaderModule, pName = "main"),
            VkPipelineShaderStageCreateInfo(stage = VkShaderStageFlagBits.VERTEX, module = vertShaderModule, pName = "main"),
        )

        val vertexAttributes = kind.vertexFormat.entries.map { entry ->
            VkVertexInputAttributeDescription(
                location = entry.attribute.location,
                binding = 0,
                format = entry.attribute.format.toVkFormat(),
                offset = entry.offsetBytes,
            )
        }.toTypedArray()

        val vertexInputInfo = arrayOf(
            VkPipelineVertexInputStateCreateInfo(
                pVertexBindingDescriptions = arrayOf(
                    VkVertexInputBindingDescription(
                        binding = 0,
                        stride = kind.vertexFormat.strideBytes,
                        inputRate = VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX,
                    ),
                ),
                pVertexAttributeDescriptions = vertexAttributes,
            ),
        )

        val inputAssembly = arrayOf(
            VkPipelineInputAssemblyStateCreateInfo(
                topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                primitiveRestartEnable = false,
            ),
        )

        val viewportState = arrayOf(
            VkPipelineViewportStateCreateInfo(
                pViewports = arrayOf(
                    VkViewport(
                        width = swapchainManager.extent.width.toFloat(),
                        height = swapchainManager.extent.height.toFloat(),
                    ),
                ),
                pScissors = arrayOf(VkRect2D(extent = swapchainManager.extent)),
            ),
        )

        val rasterizer = arrayOf(
            VkPipelineRasterizationStateCreateInfo(
                depthClampEnable = false,
                rasterizerDiscardEnable = false,
                polygonMode = VkPolygonMode.VK_POLYGON_MODE_FILL,
                lineWidth = 1.0f,
                cullMode = VkCullModeFlagBits.VK_CULL_MODE_NONE.value,
                frontFace = VkFrontFace.VK_FRONT_FACE_CLOCKWISE,
                depthBiasEnable = false,
            ),
        )

        val multisampling = arrayOf(
            VkPipelineMultisampleStateCreateInfo(
                sampleShadingEnable = false,
                rasterizationSamples = VkSampleCountFlagBits.VK_SAMPLE_COUNT_1_BIT,
            ),
        )

        val colorBlendAttachment = arrayOf(
            VkPipelineColorBlendAttachmentState(
                colorWriteMask = VkColorComponentFlagBits.VK_COLOR_COMPONENT_R_BIT.value or
                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_G_BIT.value or
                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_B_BIT.value or
                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_A_BIT.value,
                blendEnable = true,
                srcColorBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_SRC_ALPHA,
                dstColorBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                colorBlendOp = io.github.ronjunevaldoz.awake.vulkan.enums.VkBlendOp.VK_BLEND_OP_ADD,
                srcAlphaBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_SRC_ALPHA,
                dstAlphaBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                alphaBlendOp = io.github.ronjunevaldoz.awake.vulkan.enums.VkBlendOp.VK_BLEND_OP_ADD,
            ),
        )

        val colorBlending = arrayOf(
            VkPipelineColorBlendStateCreateInfo(
                logicOpEnable = false,
                pAttachments = colorBlendAttachment,
            ),
        )

        val dynamicStates = arrayOf(
            VkDynamicState.VK_DYNAMIC_STATE_VIEWPORT,
            VkDynamicState.VK_DYNAMIC_STATE_SCISSOR,
        )
        val dynamicStateInfo = arrayOf(VkPipelineDynamicStateCreateInfo(pDynamicStates = dynamicStates))

        val depthStencil = arrayOf(
            VkPipelineDepthStencilStateCreateInfo(
                depthTestEnable = false,
                depthWriteEnable = false,
                depthCompareOp = VkCompareOp.VK_COMPARE_OP_NEVER,
                depthBoundsTestEnable = false,
                stencilTestEnable = false,
            ),
        )

        val pipelineLayoutInfo = VkPipelineLayoutCreateInfo(
            pSetLayouts = arrayOf(descriptorSetLayout),
            pPushConstantRanges = arrayOf(),
        )
        pipelineLayout = Vulkan.vkCreatePipelineLayout(device, pipelineLayoutInfo)
        pipelineCache = Vulkan.vkCreatePipelineCache(device, VkPipelineCacheCreateInfo())

        val pipelineInfo = arrayOf(
            VkGraphicsPipelineCreateInfo(
                pStages = shaderStages,
                pVertexInputState = vertexInputInfo,
                pInputAssemblyState = inputAssembly,
                pViewportState = viewportState,
                pRasterizationState = rasterizer,
                pMultisampleState = multisampling,
                pColorBlendState = colorBlending,
                pDynamicState = dynamicStateInfo,
                pDepthStencilState = depthStencil,
                layout = pipelineLayout,
                renderPass = renderPass,
                subpass = 0,
            ),
        )

        graphicsPipeline = Vulkan.vkCreateGraphicsPipelines(device, pipelineCache, pipelineInfo)
        Vulkan.vkDestroyShaderModule(device, fragShaderModule)
        Vulkan.vkDestroyShaderModule(device, vertShaderModule)
    }

    fun bind(commandBuffer: Long, customDescriptorSet: Long? = null) {
        Vulkan.vkCmdBindPipeline(
            commandBuffer,
            VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
            graphicsPipeline[0],
        )
        val setToBind = customDescriptorSet ?: descriptorSet
        if (setToBind != 0L) {
            VulkanDescriptors.vkCmdBindDescriptorSet(
                commandBuffer,
                pipelineLayout,
                0,
                setToBind,
            )
        }
    }

    fun destroy() {
        descriptorSlotsByFrame.forEach { slots ->
            slots.forEach { slot ->
                VulkanDescriptors.vkDestroyDescriptorPool(device, slot.descriptorPool)
            }
            slots.clear()
        }
        if (descriptorPool != 0L) {
            VulkanDescriptors.vkDestroyDescriptorPool(device, descriptorPool)
            descriptorPool = 0
        }
        if (descriptorSetLayout != 0L) {
            VulkanDescriptors.vkDestroyDescriptorSetLayout(device, descriptorSetLayout)
            descriptorSetLayout = 0
        }
        if (screenSizeBuffer != 0L) {
            VulkanBuffers.vkDestroyBuffer(device, screenSizeBuffer)
            VulkanBuffers.vkFreeMemory(device, screenSizeBufferMemory)
            screenSizeBuffer = 0
            screenSizeBufferMemory = 0
        }
        graphicsPipeline.forEach { pipeline ->
            Vulkan.vkDestroyPipeline(device, pipeline)
        }
        graphicsPipeline = longArrayOf()
        if (pipelineLayout != 0L) {
            Vulkan.vkDestroyPipelineLayout(device, pipelineLayout)
            pipelineLayout = 0
        }
        if (pipelineCache != 0L) {
            Vulkan.vkDestroyPipelineCache(device, pipelineCache)
            pipelineCache = 0
        }
        if (ownsRenderPass && renderPass != 0L) {
            Vulkan.vkDestroyRenderPass(device, renderPass)
            renderPass = 0
        }
    }

    companion object {
        private const val SCREEN_SIZE_UNIFORM_BYTES = 16
    }
}
