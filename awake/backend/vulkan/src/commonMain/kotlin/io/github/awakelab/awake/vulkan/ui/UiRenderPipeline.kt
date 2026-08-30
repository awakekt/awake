/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.ui

import io.github.awakelab.awake.render.passes2d.UiPipelineKind
import io.github.awakelab.awake.render.pipeline.UiPipelineDescriptor
import io.github.awakelab.awake.vulkan.VK_SUBPASS_EXTERNAL
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.enums.VkAttachmentLoadOp
import io.github.awakelab.awake.vulkan.enums.VkBlendFactor
import io.github.awakelab.awake.vulkan.enums.VkColorComponentFlagBits
import io.github.awakelab.awake.vulkan.enums.VkCompareOp
import io.github.awakelab.awake.vulkan.enums.VkCullModeFlagBits
import io.github.awakelab.awake.vulkan.enums.VkDynamicState
import io.github.awakelab.awake.vulkan.enums.VkFrontFace
import io.github.awakelab.awake.vulkan.enums.VkImageLayout
import io.github.awakelab.awake.vulkan.enums.VkPipelineBindPoint
import io.github.awakelab.awake.vulkan.enums.VkPolygonMode
import io.github.awakelab.awake.vulkan.enums.VkPrimitiveTopology
import io.github.awakelab.awake.vulkan.enums.VkSampleCountFlagBits
import io.github.awakelab.awake.vulkan.enums.VkShaderStageFlagBits
import io.github.awakelab.awake.vulkan.enums.VkVertexInputRate
import io.github.awakelab.awake.vulkan.enums.flags.VkAccessFlagBits
import io.github.awakelab.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import io.github.awakelab.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import io.github.awakelab.awake.vulkan.gen.VulkanBuffers
import io.github.awakelab.awake.vulkan.gen.VulkanDescriptors
import io.github.awakelab.awake.vulkan.models.VkAttachmentDescription
import io.github.awakelab.awake.vulkan.models.VkAttachmentReference
import io.github.awakelab.awake.vulkan.models.VkRect2D
import io.github.awakelab.awake.vulkan.models.VkSubpassDependency
import io.github.awakelab.awake.vulkan.models.VkViewport
import io.github.awakelab.awake.vulkan.models.info.VkBufferCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkBufferUsageFlagBits
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorBufferInfo
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorImageInfo
import io.github.awakelab.awake.core.geometry.toByteArrayLE
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorPoolSize
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorType
import io.github.awakelab.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkMemoryAllocateInfo
import io.github.awakelab.awake.vulkan.models.info.VkRenderPassCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkSubpassDescription
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineCacheCreateInfo
import io.github.awakelab.awake.vulkan.models.info.pipeline.VkPipelineColorBlendAttachmentState
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
import io.github.awakelab.awake.vulkan.pipeline.createShaderModule
import io.github.awakelab.awake.vulkan.pipeline.toShaderIntArray
import io.github.awakelab.awake.vulkan.pipeline.toVkFormat
import io.github.awakelab.awake.vulkan.swapchain.SwapchainManager
import io.github.awakelab.awake.vulkan.texture.Texture
import io.github.awakelab.awake.core.graphics2d.BlendMode
import io.github.awakelab.awake.render.renderer.UiTargetCompositeMode

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
    private val blendMode: BlendMode = BlendMode.SourceOver,
    private val premultiplied: Boolean = false,
    /** Fullscreen two-texture composite instead of a regular UI primitive pipeline. */
    private val targetComposite: Boolean = false,
    private val vertexEntryPoint: String = "vertexMain",
    private val fragmentEntryPoint: String = "fragmentMain",
) {
    constructor(
        graphicsDevice: GraphicsDevice,
        swapchainManager: SwapchainManager,
        vertShaderCode: ByteArray,
        fragShaderCode: ByteArray,
        descriptor: UiPipelineDescriptor,
        externalRenderPass: Long? = null,
        framesInFlight: Int = 1,
        fixedTexture: Texture? = null,
    ) : this(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = vertShaderCode,
        fragShaderCode = fragShaderCode,
        kind = when (descriptor.variant) {
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Quad -> UiPipelineKind.Quad
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.RoundedQuad -> UiPipelineKind.RoundedQuad
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Glyph -> UiPipelineKind.Glyph
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Texture -> UiPipelineKind.Texture
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.TargetComposite -> UiPipelineKind.Quad
        },
        hasTexture = descriptor.variant == io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Texture || descriptor.variant == io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Glyph,
        externalRenderPass = externalRenderPass,
        framesInFlight = framesInFlight,
        fixedTexture = fixedTexture,
        blendMode = descriptor.blendMode,
        premultiplied = descriptor.isPremultiplied,
        targetComposite = descriptor.variant == io.github.awakelab.awake.render.pipeline.UiPipelineVariant.TargetComposite,
    )

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

    private var screenWidth = 0f
    private var screenHeight = 0f
    private var fontIsDistanceField = false
    private var fontRangePx = 0f

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
            if (!targetComposite && (!hasTexture || fixedTexture != null)) {
                createFixedDescriptorSet(fixedTexture)
            }
            createGraphicsPipeline(vertShaderCode, fragShaderCode)
            if (!targetComposite) writeScreenSize(swapchainManager.extent.width.toFloat(), swapchainManager.extent.height.toFloat())
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
        val bindings = if (targetComposite) {
            arrayOf(
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
                VkDescriptorSetLayoutBinding(
                    binding = 2,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                    stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                ),
                VkDescriptorSetLayoutBinding(
                    binding = 3,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
                    stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                ),
                VkDescriptorSetLayoutBinding(
                    binding = 4,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                ),
            )
        } else if (hasTexture) {
            arrayOf(
                VkDescriptorSetLayoutBinding(
                    binding = 0,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    stageFlags = screenSizeStageFlags,
                ),
                VkDescriptorSetLayoutBinding(
                    binding = 1,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                    stageFlags = VkShaderStageFlagBits.FRAGMENT.value,
                ),
                VkDescriptorSetLayoutBinding(
                    binding = 2,
                    descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
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
                VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, descriptorCount = 1),
                VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER, descriptorCount = 1),
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
                VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                VkDescriptorImageInfo(
                    sampler = 0,
                    imageView = fixedTexture.imageView.handle,
                ),
            )
            VulkanDescriptors.vkUpdateDescriptorSetImage(
                device,
                descriptorSet,
                2,
                VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
                VkDescriptorImageInfo(
                    sampler = fixedTexture.sampler.handle,
                    imageView = 0,
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
        if (targetComposite) {
            val pool = VulkanDescriptors.vkCreateDescriptorPool(
                device,
                VkDescriptorPoolCreateInfo(
                    maxSets = 1,
                    pPoolSizes = arrayOf(
                        VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, descriptorCount = 2),
                        VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER, descriptorCount = 2),
                        VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount = 1),
                    ),
                ),
            )
            val set = VulkanDescriptors.vkAllocateDescriptorSet(device, pool, descriptorSetLayout)
            return TextureDescriptorSlot(pool, set)
        }
        val pool = VulkanDescriptors.vkCreateDescriptorPool(
            device,
            VkDescriptorPoolCreateInfo(
                maxSets = 1,
                pPoolSizes = arrayOf(
                    VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount = 1),
                    VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, descriptorCount = 1),
                    VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER, descriptorCount = 1),
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
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
            VkDescriptorImageInfo(
                sampler = 0,
                imageView = imageView,
            ),
        )
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            slot.descriptorSet,
            2,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
            VkDescriptorImageInfo(
                sampler = sampler,
                imageView = 0,
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

    /** Binds the two sampled inputs and mode uniform for a target composite. */
    fun bindTargetComposite(
        commandBuffer: Long,
        frameIndex: Int,
        sourceSampler: Long,
        sourceImageView: Long,
        destinationSampler: Long,
        destinationImageView: Long,
        mode: UiTargetCompositeMode,
    ) {
        check(targetComposite) { "bindTargetComposite requires a target-composite UiRenderPipeline." }
        val slot = descriptorSlot(frameIndex, 0)
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device, slot.descriptorSet, 0, VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
            VkDescriptorImageInfo(sampler = 0, imageView = sourceImageView),
        )
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device, slot.descriptorSet, 1, VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
            VkDescriptorImageInfo(sampler = sourceSampler, imageView = 0),
        )
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device, slot.descriptorSet, 2, VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
            VkDescriptorImageInfo(sampler = 0, imageView = destinationImageView),
        )
        VulkanDescriptors.vkUpdateDescriptorSetBuffer(
            device,
            slot.descriptorSet,
            4,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
            VkDescriptorBufferInfo(buffer = screenSizeBuffer, range = SCREEN_SIZE_UNIFORM_BYTES.toLong()),
        )
        VulkanBuffers.writeBufferMemoryBytes(
            device,
            screenSizeBufferMemory,
            0,
            intArrayOf(mode.ordinal).toByteArrayLE(),
        )
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device, slot.descriptorSet, 3, VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
            VkDescriptorImageInfo(sampler = destinationSampler, imageView = 0),
        )
        bind(commandBuffer, slot.descriptorSet)
    }

    fun prepareDescriptorSet(texture: Texture, frameIndex: Int = 0): Long {
        val slot = descriptorSlot(frameIndex, 0)
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            slot.descriptorSet,
            1,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
            VkDescriptorImageInfo(
                sampler = 0,
                imageView = texture.imageView.handle,
            ),
        )
        VulkanDescriptors.vkUpdateDescriptorSetImage(
            device,
            slot.descriptorSet,
            2,
            VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
            VkDescriptorImageInfo(
                sampler = texture.sampler.handle,
                imageView = 0,
            ),
        )
        return slot.descriptorSet
    }

    fun writeScreenSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        writeUiUniforms()
    }

    private fun writeUiUniforms() {
        VulkanBuffers.writeBufferMemoryFloats(
            device,
            screenSizeBufferMemory,
            0,
            floatArrayOf(
                2f / screenWidth,
                2f / screenHeight,
                -1f,
                -1f,
                if (fontIsDistanceField) 1f else 0f,
                fontRangePx,
                0f,
                0f,
            ),
        )
    }

    /**
     * Writes the glyph-shader font-info fields (bytes 8–15 of the shared UBO).
     *
     * The glyph fragment shader reads:
     *   fontInfo.x — 1.0 for a distance-field atlas, 0.0 for a coverage-alpha atlas.
     *   fontInfo.y — distanceFieldRangePx (the `-pxrange` the atlas was generated with,
     *                in atlas texels). The shader divides by atlasSize to recover UV-space
     *                range; handing it zero makes screenPxRange collapse to 1, producing
     *                a hard-edge 1px band regardless of the glyph's on-screen size.
     *
     * Call once after [writeScreenSize] when the glyph pipeline is first constructed or
     * when the font changes. Screen-resize events only need to re-call [writeScreenSize].
     */
    fun writeFontInfo(isDistanceField: Boolean, rangePx: Float) {
        fontIsDistanceField = isDistanceField
        fontRangePx = rangePx
        // Write the complete UBO from offset zero. Partial mapped writes at offset 8 are not
        // portable: Vulkan requires vkMapMemory offsets to satisfy minMemoryMapAlignment.
        writeUiUniforms()
    }


    private fun createGraphicsPipeline(vertShaderCode: ByteArray, fragShaderCode: ByteArray) {
        val fragShaderModule = createShaderModule(device, fragShaderCode.toShaderIntArray())
        val vertShaderModule = createShaderModule(device, vertShaderCode.toShaderIntArray())

        val shaderStages = arrayOf(
            VkPipelineShaderStageCreateInfo(stage = VkShaderStageFlagBits.FRAGMENT, module = fragShaderModule, pName = fragmentEntryPoint),
            VkPipelineShaderStageCreateInfo(stage = VkShaderStageFlagBits.VERTEX, module = vertShaderModule, pName = vertexEntryPoint),
        )

        val vertexAttributes = if (targetComposite) emptyArray() else kind.vertexFormat.entries.map { entry ->
            VkVertexInputAttributeDescription(
                location = entry.attribute.location,
                binding = 0,
                format = entry.attribute.format.toVkFormat(),
                offset = entry.offsetBytes,
            )
        }.toTypedArray()

        val vertexInputInfo = arrayOf(
            VkPipelineVertexInputStateCreateInfo(
                pVertexBindingDescriptions = if (targetComposite) emptyArray() else arrayOf(
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
                blendEnable = !targetComposite,
                srcColorBlendFactor = when (blendMode) {
                    BlendMode.SourceOver, BlendMode.Plus -> if (premultiplied) VkBlendFactor.VK_BLEND_FACTOR_ONE else VkBlendFactor.VK_BLEND_FACTOR_SRC_ALPHA
                    BlendMode.Screen, BlendMode.Overlay -> error("$blendMode requires a sampled target composite pass.")
                },
                dstColorBlendFactor = when (blendMode) {
                    BlendMode.SourceOver -> VkBlendFactor.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
                    BlendMode.Plus -> VkBlendFactor.VK_BLEND_FACTOR_ONE
                    BlendMode.Screen, BlendMode.Overlay -> error("$blendMode requires a sampled target composite pass.")
                },
                colorBlendOp = io.github.awakelab.awake.vulkan.enums.VkBlendOp.VK_BLEND_OP_ADD,
                // Colour is straight-alpha source-over, but alpha itself must use the source
                // value directly. SRC_ALPHA here squared a 0.5 graphics layer into 0.25.
                srcAlphaBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE,
                dstAlphaBlendFactor = when (blendMode) {
                    BlendMode.SourceOver -> VkBlendFactor.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
                    BlendMode.Plus -> VkBlendFactor.VK_BLEND_FACTOR_ONE
                    BlendMode.Screen, BlendMode.Overlay -> error("$blendMode requires a sampled target composite pass.")
                },
                alphaBlendOp = io.github.awakelab.awake.vulkan.enums.VkBlendOp.VK_BLEND_OP_ADD,
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
        private const val SCREEN_SIZE_UNIFORM_BYTES = 32
    }
}
