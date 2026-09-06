/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.vulkan.VK_SUBPASS_EXTERNAL
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.debug.PerFrameUniformSlots
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkAttachmentStoreOp
import com.awakekt.awake.vulkan.enums.VkBlendFactor
import com.awakekt.awake.vulkan.enums.VkColorComponentFlagBits
import com.awakekt.awake.vulkan.enums.VkCullModeFlagBits
import com.awakekt.awake.vulkan.enums.VkDynamicState
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkFrontFace
import com.awakekt.awake.vulkan.enums.VkImageLayout
import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.enums.VkPolygonMode
import com.awakekt.awake.vulkan.enums.VkPrimitiveTopology
import com.awakekt.awake.vulkan.enums.VkShaderStageFlagBits
import com.awakekt.awake.vulkan.enums.VkVertexInputRate
import com.awakekt.awake.vulkan.enums.flags.VkAccessFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.models.VkAttachmentDescription
import com.awakekt.awake.vulkan.models.VkAttachmentReference
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkSubpassDependency
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassCreateInfo
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
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.Texture

/**
 * Owns a render pass + graphics pipeline. Takes compiled SPIR-V ([ShaderPair]) rather than a
 * shader asset path -- loading shaders is a platform/resource concern, not a pipeline one.
 * Takes [Material]'s [DescriptorSetLayoutHandle], not the whole [Material] instance -- the
 * pipeline layout only needs the layout handle. Vertex attribute layout is driven by
 * [vertexFormat]; each entry becomes one [VkVertexInputAttributeDescription].
 */
class RenderPipeline(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    /** Shared across every [RenderPipeline] a caller builds against the same swapchain --
     * see the top-level [createSceneRenderPass] this class no longer creates itself. Not
     * owned/destroyed by this class; the caller that created it destroys it exactly once. */
    val renderPass: Long,
    descriptorSetLayout: DescriptorSetLayoutHandle,
    shaders: ShaderPair,
    val vertexFormat: VertexFormat = VertexFormat.PositionColorUv,
    vertexEntryPoint: String = DEFAULT_SHADER_ENTRY_POINT,
    fragmentEntryPoint: String = DEFAULT_SHADER_ENTRY_POINT,
    /** `VK_POLYGON_MODE_LINE` builds a wireframe companion pipeline (see `Renderer.wireframe`).
     * Requires `fillModeNonSolid` -- already enabled, since `GraphicsDevice.createLogicalDevice`
     * enables every feature the device reports. */
    polygonMode: VkPolygonMode = VkPolygonMode.VK_POLYGON_MODE_FILL,
    /** `VK_CULL_MODE_NONE` (default) draws both triangle faces always -- correct for anything
     * genuinely double-sided, and the behavior every mesh had before per-mesh culling existed.
     * `VK_CULL_MODE_BACK_BIT` builds a back-culled companion pipeline (see `Renderer.pipelineFor`)
     * for a correctly-wound solid mesh -- see `render.renderer.CullMode`'s own doc comment for
     * why NONE isn't just a historical default, it's also a real z-fighting/perf trade-off. */
    cullMode: VkCullModeFlagBits = VkCullModeFlagBits.VK_CULL_MODE_NONE,
    /** See [PipelineVariant]'s own doc comment. Defaults to [PipelineVariant.Opaque] -- the
     * pipeline this class always built before any variant existed. */
    variant: PipelineVariant = PipelineVariant.Opaque,
    /** Extra descriptor set layouts appended after [descriptorSetLayout] (set 0). Today's only
     * user: the skinned-instanced pipeline's `@group(1)` joint-palette buffer. */
    extraDescriptorSetLayouts: List<DescriptorSetLayoutHandle> = emptyList(),
    /** Non-null builds this pipeline its OWN uniform block and binds that at set 0 instead of
     * [descriptorSetLayout] -- see `PipelineSpec.uniforms`. A content pipeline has no per-draw
     * material to read uniforms from, so the material layout would describe bindings its shader
     * never declares. */
    uniforms: UniformLayout? = null,
    /** How many copies of [uniforms] to allocate. Ignored when [uniforms] is null. */
    framesInFlight: Int = 1,
    /** Which engine-owned groups this pipeline declares a set layout for -- see
     * [VulkanPipelineHandle.engineBoundSemantics]. */
    override val engineBoundSemantics: Set<BindingSemantic> = emptySet(),
    override val bindingLayout: BindingLayout = BindingLayout.Standard,
    /** What this pipeline's own group holds, when it owns one -- see `PipelineSpec
     * .materialBindings`. Only meaningful alongside [uniforms]; a pipeline reading a per-draw
     * material's set has no group of its own to shape. */
    materialBindings: GroupBindings? = null,
) : VulkanPipelineHandle,
    UniformBlockOwner {
    override val pipelineHandle: Long get() = graphicsPipeline[0]
    override val pipelineLayoutHandle: Long get() = pipelineLayout

    // Read by the create* builders through the field rather than passed as a param: that's
    // exactly the parameter-count pressure this class's own doc comment on createPipelineLayout
    // exists to avoid growing further.
    private val extraDescriptorSetLayouts = extraDescriptorSetLayouts
    private val cullMode = cullMode
    private val graphicsDevice = graphicsDevice
    private val swapchainManager = swapchainManager
    private val device get() = graphicsDevice.device

    var pipelineLayout: Long = 0
    var pipelineCache: Long = 0
    var graphicsPipeline: LongArray = longArrayOf()

    // Built before the pipeline, because its descriptor set layout IS this pipeline's set 0.
    private val uniformSlots: PerFrameUniformSlots? = uniforms?.let {
        PerFrameUniformSlots(
            graphicsDevice,
            it.total * Float.SIZE_BYTES,
            VkShaderStageFlagBits.VERTEX.value or VkShaderStageFlagBits.FRAGMENT.value,
            framesInFlight,
            materialBindings,
        )
    }

    /**
     * Writes a content feature's textures into this pipeline's own descriptor sets.
     *
     * Called by the engine after the registry compiled this pipeline, because the declaration
     * and the pixels arrive at different times -- see [PerFrameUniformSlots.writeTextures]. A
     * pipeline with no group of its own has nowhere to put them, which is a caller mistake
     * rather than a no-op: the feature declared textures its pipeline cannot hold.
     */
    fun writeContentTextures(textures: Map<Int, Texture>) {
        if (textures.isEmpty()) return
        val slots = checkNotNull(uniformSlots) {
            "This pipeline owns no uniform group, so it cannot hold the ${textures.size} " +
                "texture(s) a content feature supplied for it."
        }
        slots.writeTextures(textures)
    }

    override val uniformBlock: UniformBlock? = uniformSlots?.let { slots ->
        val layout = requireNotNull(uniforms)
        object : UniformBlock {
            override fun binding(frameIndex: Int) = slots[frameIndex]
            override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) =
                slots.write(frameIndex, UniformWriter(layout).apply(fill).build())
        }
    }

    // destroy() is zero-handle-tolerant, so calling it on a partial-construction failure is
    // safe and avoids a leak. renderPass isn't touched -- it's caller-owned.
    init {
        try {
            createGraphicsPipeline(
                descriptorSetLayout = uniformSlots
                    ?.let { DescriptorSetLayoutHandle(it.descriptorSetLayout) }
                    ?: descriptorSetLayout,
                shaders = shaders,
                vertexFormat = vertexFormat,
                vertexEntryPoint = vertexEntryPoint,
                fragmentEntryPoint = fragmentEntryPoint,
                polygonMode = polygonMode,
                variant = variant,
            )
        } catch (e: Throwable) {
            destroy()
            throw e
        }
    }

    private fun createGraphicsPipeline(
        descriptorSetLayout: DescriptorSetLayoutHandle,
        shaders: ShaderPair,
        vertexFormat: VertexFormat,
        vertexEntryPoint: String,
        fragmentEntryPoint: String,
        polygonMode: VkPolygonMode,
        variant: PipelineVariant,
    ) {
        // WARNING: make sure the .spv vulkan version match, this might cause out of memory
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

        pipelineLayout = createPipelineLayout(device, descriptorSetLayout, extraDescriptorSetLayouts)

        val createInfos = arrayOf(
            VkGraphicsPipelineCreateInfo(
                pStages = shaderStages,
                pVertexInputState = vertexInputState(vertexFormat, variant),
                pInputAssemblyState = INPUT_ASSEMBLY_STATE,
                pViewportState = viewportState(swapchainManager),
                pRasterizationState = rasterizationState(polygonMode, cullMode),
                pMultisampleState = MULTISAMPLE_STATE,
                pColorBlendState = colorBlendState(variant.blendEnabled),
                pDepthStencilState = depthStencilState(variant.depthTestEnabled, variant.depthWriteEnabled),
                pDynamicState = DYNAMIC_STATE,
                layout = pipelineLayout,
                renderPass = renderPass,
                subpass = 0,
                basePipelineHandle = 0, // Optional
                basePipelineIndex = -1, // Optional
            ),
        )
        pipelineCache = Vulkan.vkCreatePipelineCache(device, VkPipelineCacheCreateInfo())
        graphicsPipeline = Vulkan.vkCreateGraphicsPipelines(device, pipelineCache, createInfos)

        Vulkan.vkDestroyShaderModule(device, fragShaderModule)
        Vulkan.vkDestroyShaderModule(device, vertShaderModule)
    }

    /** Binds the (single) graphics pipeline. Render-pass begin/end and descriptor-set
     * binding (a [Material] concern) happen around/after this, not here. */
    fun bind(commandBuffer: Long) {
        Vulkan.vkCmdBindPipeline(
            commandBuffer,
            VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
            graphicsPipeline[0],
        )
    }

    fun destroy() {
        graphicsPipeline.forEach { pipeline ->
            Vulkan.vkDestroyPipeline(device, pipeline)
        }
        Vulkan.vkDestroyPipelineLayout(device, pipelineLayout)
        Vulkan.vkDestroyPipelineCache(device, pipelineCache)
        uniformSlots?.destroy()
    }
}

private const val DEFAULT_SHADER_ENTRY_POINT = "main"

/** Render pass shared by every [RenderPipeline] built against the same [swapchainManager] --
 * avoids one near-identical render pass per pipeline. Not owned by any pipeline; caller
 * destroys it exactly once. */
fun createSceneRenderPass(graphicsDevice: GraphicsDevice, swapchainManager: SwapchainManager): Long =
    Vulkan.vkCreateRenderPass(
        graphicsDevice.device,
        VkRenderPassCreateInfo(
            pAttachments = arrayOf(
                VkAttachmentDescription(
                    format = swapchainManager.imageFormat,
                    initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
                    // COLOR_ATTACHMENT_OPTIMAL, not PRESENT_SRC_KHR: the UI overlay pass
                    // (UiRenderPipeline) draws on top of this pass's output before the
                    // final present-layout transition happens, at the end of that pass
                    // instead. See docs/reference/decision-log.md's custom-UI entries.
                    finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                ),
                VkAttachmentDescription(
                    format = VkFormat.VK_FORMAT_D32_SFLOAT,
                    storeOp = VkAttachmentStoreOp.DONT_CARE,
                    initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
                    finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
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
                    pDepthStencilAttachment = arrayOf(
                        VkAttachmentReference(
                            attachment = 1,
                            layout = VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                        ),
                    ),
                ),
            ),
            pDependencies = arrayOf(
                VkSubpassDependency(
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
                ),
            ),
        ),
    )

/** See [PipelineVariant.instanced]. Binding 1 (binding 0 is the mesh's own per-vertex buffer);
 * one `mat4` = 4 `vec4` rows = 64 bytes per instance. */
private const val INSTANCE_BINDING = 1
private const val MATRIX_ROWS = 4
private const val VEC4_BYTES = 16
private const val INSTANCE_MATRIX_BYTES = MATRIX_ROWS * VEC4_BYTES

/** See [PipelineVariant.instanceAlpha]. Binding 2 -- one binding past the instance matrix's own
 * binding 1. */
private const val INSTANCE_ALPHA_BINDING = 2

/** See [PipelineVariant.instanceFrame]. Binding 3 -- one binding past the instance color's own
 * binding 2. */
private const val INSTANCE_FRAME_BINDING = 3

/** Fixed structs that never vary per pipeline -- built once instead of reconstructed on every
 * `RenderPipeline`'s own `createGraphicsPipeline` call. */
private val DYNAMIC_STATE = arrayOf(
    VkPipelineDynamicStateCreateInfo(
        pDynamicStates = arrayOf(
            VkDynamicState.VK_DYNAMIC_STATE_VIEWPORT,
            VkDynamicState.VK_DYNAMIC_STATE_SCISSOR,
        ),
    ),
)
private val MULTISAMPLE_STATE = arrayOf(VkPipelineMultisampleStateCreateInfo())

// Specify we will use triangle lists to draw geometry.
private val INPUT_ASSEMBLY_STATE = arrayOf(
    VkPipelineInputAssemblyStateCreateInfo(
        topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
        primitiveRestartEnable = false,
    ),
)

/** One `VK_VERTEX_INPUT_RATE_INSTANCE` binding plus its attribute(s) -- [attributes] is
 * `(offsetBytes, format)` per attribute, at consecutive locations starting at [firstLocation].
 * Both the model-matrix binding (4 attributes, one per row) and the alpha binding (1
 * attribute) are this same shape, just different attribute counts/formats. Top-level, not a
 * [RenderPipeline] member: it's a pure function of its params, and every member here that
 * doesn't need instance state stays out of the class to keep it under detekt's
 * `TooManyFunctions` threshold. */
private fun instanceRateBinding(
    binding: Int,
    strideBytes: Int,
    firstLocation: Int,
    attributes: List<Pair<Int, VkFormat>>,
): Pair<VkVertexInputBindingDescription, List<VkVertexInputAttributeDescription>> {
    val bindingDescription = VkVertexInputBindingDescription(
        binding = binding,
        stride = strideBytes,
        inputRate = VkVertexInputRate.VK_VERTEX_INPUT_RATE_INSTANCE,
    )
    val attributeDescriptions = attributes.mapIndexed { index, (offset, format) ->
        VkVertexInputAttributeDescription(
            location = firstLocation + index,
            binding = binding,
            format = format,
            offset = offset,
        )
    }
    return bindingDescription to attributeDescriptions
}

private fun vertexInputState(
    vertexFormat: VertexFormat,
    variant: PipelineVariant,
): Array<VkPipelineVertexInputStateCreateInfo> {
    // No binding at all for VertexFormat.None -- a stride-0 binding no attribute reads is not the
    // same thing as declaring the pipeline takes no vertex buffer.
    val bindings = mutableListOf<VkVertexInputBindingDescription>()
    if (vertexFormat.attributes.isNotEmpty()) {
        bindings += VkVertexInputBindingDescription(
            binding = 0,
            stride = vertexFormat.strideBytes,
            inputRate = VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX,
        )
    }
    val attributes = vertexFormat.entries.mapTo(mutableListOf()) { entry ->
        VkVertexInputAttributeDescription(
            location = entry.attribute.location,
            binding = 0,
            format = entry.attribute.format.toVkFormat(),
            offset = entry.offsetBytes,
        )
    }
    if (variant.instanced) {
        // maxOfOrNull, not maxOf: an instanced pipeline over VertexFormat.None has no per-vertex
        // location to count past, so instance attributes start at 0.
        val firstLocation = (vertexFormat.attributes.maxOfOrNull { it.location } ?: -1) + 1
        val (matrixBinding, matrixAttributes) = instanceRateBinding(
            INSTANCE_BINDING,
            INSTANCE_MATRIX_BYTES,
            firstLocation,
            (0 until MATRIX_ROWS).map { it * VEC4_BYTES to VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT },
        )
        bindings += matrixBinding
        attributes += matrixAttributes
        if (variant.instanceAlpha) {
            // One vec4 (rgba) per instance, not a lone float -- alpha rides in .w alongside
            // per-particle color, see DrawCall.instanceColors' own doc comment.
            val (colorBinding, colorAttributes) = instanceRateBinding(
                INSTANCE_ALPHA_BINDING,
                VEC4_BYTES,
                firstLocation + MATRIX_ROWS,
                listOf(0 to VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT),
            )
            bindings += colorBinding
            attributes += colorAttributes
            if (variant.instanceFrame) {
                // One f32 per instance -- this particle's own sprite-strip frame index, see
                // DrawCall.instanceFrames' own doc comment.
                val (frameBinding, frameAttributes) = instanceRateBinding(
                    INSTANCE_FRAME_BINDING,
                    Float.SIZE_BYTES,
                    firstLocation + MATRIX_ROWS + 1,
                    listOf(0 to VkFormat.VK_FORMAT_R32_SFLOAT),
                )
                bindings += frameBinding
                attributes += frameAttributes
            }
        }
    }
    return arrayOf(
        VkPipelineVertexInputStateCreateInfo(
            pVertexBindingDescriptions = bindings.toTypedArray(),
            pVertexAttributeDescriptions = attributes.toTypedArray(),
        ),
    )
}

private fun viewportState(swapchainManager: SwapchainManager): Array<VkPipelineViewportStateCreateInfo> = arrayOf(
    VkPipelineViewportStateCreateInfo(
        pViewports = arrayOf(
            VkViewport(
                width = swapchainManager.extent.width.toFloat(),
                height = swapchainManager.extent.height.toFloat(),
            ),
        ),
        pScissors = arrayOf(
            VkRect2D(
                offset = VkOffset2D(),
                extent = swapchainManager.extent,
            ),
        ),
    ),
)

private fun depthStencilState(
    depthTestEnabled: Boolean,
    depthWriteEnabled: Boolean,
): Array<VkPipelineDepthStencilStateCreateInfo> = arrayOf(
    VkPipelineDepthStencilStateCreateInfo(
        depthTestEnable = depthTestEnabled,
        depthWriteEnable = depthWriteEnabled,
    ),
)

private fun rasterizationState(
    polygonMode: VkPolygonMode,
    cullMode: VkCullModeFlagBits,
): Array<VkPipelineRasterizationStateCreateInfo> = arrayOf(
    VkPipelineRasterizationStateCreateInfo(
        cullMode = cullMode.value,
        frontFace = VkFrontFace.VK_FRONT_FACE_CLOCKWISE,
        polygonMode = polygonMode,
        lineWidth = 1f,
    ),
)

private fun colorBlendState(blendEnabled: Boolean): Array<VkPipelineColorBlendStateCreateInfo> {
    val blendAttachment = VkPipelineColorBlendAttachmentState(
        blendEnable = blendEnabled,
        srcColorBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_SRC_ALPHA,
        dstColorBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
        srcAlphaBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_SRC_ALPHA,
        dstAlphaBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
        colorWriteMask = VkColorComponentFlagBits.VK_COLOR_COMPONENT_R_BIT.value or
            VkColorComponentFlagBits.VK_COLOR_COMPONENT_G_BIT.value or
            VkColorComponentFlagBits.VK_COLOR_COMPONENT_B_BIT.value or
            VkColorComponentFlagBits.VK_COLOR_COMPONENT_A_BIT.value,
    )
    return arrayOf(VkPipelineColorBlendStateCreateInfo(pAttachments = arrayOf(blendAttachment)))
}

private fun createPipelineLayout(
    device: Long,
    descriptorSetLayout: DescriptorSetLayoutHandle,
    extraDescriptorSetLayouts: List<DescriptorSetLayoutHandle>,
): Long = Vulkan.vkCreatePipelineLayout(
    device,
    VkPipelineLayoutCreateInfo(
        pSetLayouts = (
            listOf(descriptorSetLayout.handle) + extraDescriptorSetLayouts.map { it.handle }
            ).toTypedArray(),
    ),
)

internal fun GpuDataShape.toVkFormat(): VkFormat = when (this) {
    GpuDataShape.Float -> VkFormat.VK_FORMAT_R32_SFLOAT
    GpuDataShape.Vec2 -> VkFormat.VK_FORMAT_R32G32_SFLOAT
    GpuDataShape.Vec3 -> VkFormat.VK_FORMAT_R32G32B32_SFLOAT
    GpuDataShape.Vec4 -> VkFormat.VK_FORMAT_R32G32B32A32_SFLOAT
    GpuDataShape.UInt4 -> VkFormat.VK_FORMAT_R32G32B32A32_UINT
    GpuDataShape.Mat4 -> error("Mat4 is not a valid vertex-attribute format.")
}
