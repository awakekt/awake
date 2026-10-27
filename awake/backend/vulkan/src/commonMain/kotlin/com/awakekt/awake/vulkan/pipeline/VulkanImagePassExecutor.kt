/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassShaderSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTargetFormat
import com.awakekt.awake.render.imagepass.ImagePassTargetSpec
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.VK_SUBPASS_EXTERNAL
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkAttachmentLoadOp
import com.awakekt.awake.vulkan.enums.VkAttachmentStoreOp
import com.awakekt.awake.vulkan.enums.VkBlendFactor
import com.awakekt.awake.vulkan.enums.VkBlendOp
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
import com.awakekt.awake.vulkan.material.materialLayoutBindings
import com.awakekt.awake.vulkan.material.materialPoolSizes
import com.awakekt.awake.vulkan.models.VkAttachmentDescription
import com.awakekt.awake.vulkan.models.VkAttachmentReference
import com.awakekt.awake.vulkan.models.VkClearColorValue
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkSubpassDependency
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkDescriptorBufferInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorImageInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import com.awakekt.awake.vulkan.models.info.VkFilter
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
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
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.vulkan.ui.DynamicMesh
import kotlin.math.roundToInt

/**
 * Describes the active destination surface that a [VulkanImagePassExecutor] interrupts, snapshots,
 * and resumes when executing an [ImagePassChain].
 */
internal data class VulkanImagePassParentTarget(
    val framebuffer: Long,
    val colorImage: Long,
    val colorImageView: Long,
    val colorFormat: VkFormat,
    val width: Int,
    val height: Int,
    val isOffscreen: Boolean,
    val finalRenderPass: Long,
    val sampleable: Boolean = true,
)

/**
 * Generic Vulkan executor for multi-step [ImagePassChain] graphs.
 *
 * Compiles and caches WGSL shaders via [NagaShaderCompiler] by [ImagePassShaderSpec.id], pools
 * transient render targets by `(id, width, height, format)`, and handles mid-pass interruption and
 * resumption with `loadOp = LOAD`.
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class VulkanImagePassExecutor(
    private val graphicsDevice: GraphicsDevice,
    private val maxFramesInFlight: Int,
    private val meshProvider: (Int) -> DynamicMesh,
    private val textureProvider: (TextureAsset) -> Texture,
) {
    private val device: Long get() = graphicsDevice.device
    private val physicalDevice: Long get() = graphicsDevice.physicalDevice

    private val transientTargets = LinkedHashMap<String, PooledTransientTarget>()
    private val transientClearRenderPasses = HashMap<VkFormat, Long>()
    private val transientLoadRenderPasses = HashMap<VkFormat, Long>()
    private val swapchainContinueRenderPasses = HashMap<VkFormat, Long>()
    private val offscreenContinueRenderPasses = HashMap<VkFormat, Long>()
    private val offscreenClearRenderPasses = HashMap<VkFormat, Long>()
    private val samplers = HashMap<ImagePassSamplerSpec, Long>()
    private val compiledShaders = HashMap<String, CompiledShader>()
    private val pipelines = HashMap<PipelineKey, Long>()
    private val stepSlotsByFrame = Array(maxFramesInFlight.coerceAtLeast(1)) {
        HashMap<String, MutableList<StepDescriptorSlot>>()
    }
    private val stepSlotsUsedByFrame = Array(maxFramesInFlight.coerceAtLeast(1)) {
        HashMap<String, Int>()
    }
    private val meshStepsUsedByFrame = IntArray(maxFramesInFlight.coerceAtLeast(1))

    /** Number of transient targets currently held in the pool. */
    internal val pooledTargetCount: Int
        get() = transientTargets.size

    /** Total number of transient GPU image allocations performed over this executor's lifetime. */
    internal var totalTargetAllocations: Int = 0
        private set

    /** Total number of transient target lookups satisfied from the pool without reallocation. */
    internal var totalTargetReuses: Int = 0
        private set

    /** Returns `(width, height)` for the pooled target [id], or `null` if not allocated. */
    internal fun pooledTargetDimensions(id: String): Pair<Int, Int>? =
        transientTargets[id]?.let { it.width to it.height }

    /** Resets per-frame dynamic descriptor and mesh slot counters before recording a new frame. */
    fun beginFrame(frameIndex: Int) {
        val safeFrame = frameIndex.coerceIn(0, maxFramesInFlight.coerceAtLeast(1) - 1)
        stepSlotsUsedByFrame[safeFrame].clear()
        meshStepsUsedByFrame[safeFrame] = 0
    }

    /**
     * Render pass compatible with the 1-attachment swapchain UI pass that preserves color contents
     * (`loadOp = LOAD`) and leaves the image in `VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL` so an
     * image pass can transition it to `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`.
     */
    fun ensureSwapchainContinueRenderPass(colorFormat: VkFormat): Long =
        swapchainContinueRenderPasses.getOrPut(colorFormat) {
            // The dependency must equal the UI pass's own: validation treats it as part of render
            // pass compatibility, and this pass reuses the UI framebuffers and pipelines.
            createSingleColorRenderPass(
                colorFormat = colorFormat,
                loadOp = VkAttachmentLoadOp.LOAD,
                initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                srcAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
            )
        }

    /**
     * Single-sample colour-only pass over an offscreen resolved image. Preserves color
     * contents (`loadOp = LOAD`) and leaves the color image in `VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL`.
     */
    fun ensureOffscreenContinueRenderPass(colorFormat: VkFormat): Long =
        offscreenContinueRenderPasses.getOrPut(colorFormat) {
            createOffscreenContinueRenderPass(colorFormat)
        }

    /**
     * Interrupts the active render pass on [parentTarget], executes [chain]'s transient and
     * composite steps, and resumes [parentTarget]'s render pass with `loadOp = LOAD`.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList", "NestedBlockDepth")
    fun executeImagePass(
        commandBuffer: Long,
        frameIndex: Int,
        parentTarget: VulkanImagePassParentTarget,
        chain: ImagePassChain,
        hasMoreImagePasses: Boolean,
        stats: RenderStatsCounter,
    ) {
        val safeFrame = frameIndex.coerceIn(0, maxFramesInFlight.coerceAtLeast(1) - 1)
        val canExecute = !chain.isEmpty && (!chain.requiresDestinationSnapshot || parentTarget.sampleable)
        if (!canExecute) {
            if (!parentTarget.isOffscreen && !hasMoreImagePasses) {
                Vulkan.vkCmdEndRenderPass(commandBuffer)
                beginParentRenderPass(commandBuffer, parentTarget, parentTarget.finalRenderPass)
            }
            return
        }

        val referenceWidth = chain.sourceBounds.width.roundToInt().coerceAtLeast(1)
        val referenceHeight = chain.sourceBounds.height.roundToInt().coerceAtLeast(1)
        val activeTransients = HashMap<String, PooledTransientTarget>(chain.transientTargets.size)
        for (spec in chain.transientTargets) {
            activeTransients[spec.id] = acquireTransientTarget(
                spec = spec,
                destinationFormat = parentTarget.colorFormat,
                referenceWidth = referenceWidth,
                referenceHeight = referenceHeight,
            )
        }

        Vulkan.vkCmdEndRenderPass(commandBuffer)
        var parentInShaderReadOnly = false
        var parentPassOpen = false
        var currentParentRenderPass = 0L

        for (stepIndex in chain.steps.indices) {
            val step = chain.steps[stepIndex]
            val readsSnapshot = step.inputs.any { it.source == ImagePassSource.DestinationSnapshot }
            if (readsSnapshot) {
                if (parentPassOpen) {
                    Vulkan.vkCmdEndRenderPass(commandBuffer)
                    parentPassOpen = false
                }
                if (!parentInShaderReadOnly) {
                    VulkanImages.vkTransitionImageLayout(
                        commandBuffer,
                        parentTarget.colorImage,
                        VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    )
                    parentInShaderReadOnly = true
                }
            }

            when (val destination = step.destination) {
                is ImagePassDestination.TransientTarget -> {
                    if (parentPassOpen) {
                        Vulkan.vkCmdEndRenderPass(commandBuffer)
                        parentPassOpen = false
                    }
                    val target = checkNotNull(activeTransients[destination.id]) {
                        "ImagePassStep references undeclared transient target '${destination.id}'."
                    }
                    val useClear = step.clearDestination || !target.inShaderReadOnly
                    val renderPass = if (useClear) {
                        ensureTransientClearRenderPass(target.format)
                    } else {
                        ensureTransientLoadRenderPass(target.format)
                    }
                    val framebuffer = if (useClear) target.clearFramebuffer else target.loadFramebuffer
                    Vulkan.vkCmdBeginRenderPass(
                        commandBuffer,
                        VkRenderPassBeginInfo(
                            renderPass = renderPass,
                            framebuffer = framebuffer,
                            renderArea = VkRect2D(extent = VkExtent2D(target.width, target.height)),
                            pClearValues = if (useClear) {
                                arrayOf(VkClearColorValue.rgba(0f, 0f, 0f, 0f))
                            } else {
                                emptyArray()
                            },
                        ),
                        VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
                    )
                    Vulkan.vkCmdSetViewport(
                        commandBuffer,
                        0,
                        arrayOf(VkViewport(width = target.width.toFloat(), height = target.height.toFloat())),
                    )
                    Vulkan.vkCmdSetScissor(
                        commandBuffer,
                        0,
                        arrayOf(VkRect2D(extent = VkExtent2D(target.width, target.height))),
                    )
                    recordStepDraw(
                        commandBuffer = commandBuffer,
                        frameIndex = safeFrame,
                        step = step,
                        renderPass = renderPass,
                        blendEnabled = false,
                        blendMode = BlendMode.SourceOver,
                        isPremultiplied = true,
                        parentTarget = parentTarget,
                        activeTransients = activeTransients,
                        stats = stats,
                    )
                    Vulkan.vkCmdEndRenderPass(commandBuffer)
                    target.inShaderReadOnly = true
                    VulkanImages.vkCmdMemoryBarrier(
                        commandBuffer,
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                        VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
                        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value,
                        VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value,
                    )
                }

                is ImagePassDestination.ActiveTarget -> {
                    val willInterruptAgain = hasMoreImagePasses ||
                        chain.steps.subList(stepIndex + 1, chain.steps.size).any { later ->
                            later.destination is ImagePassDestination.TransientTarget ||
                                later.inputs.any { it.source == ImagePassSource.DestinationSnapshot }
                        }
                    val desiredRenderPass = resolveParentResumeRenderPass(parentTarget, willInterruptAgain)
                    if (!parentPassOpen || currentParentRenderPass != desiredRenderPass) {
                        if (parentPassOpen) {
                            Vulkan.vkCmdEndRenderPass(commandBuffer)
                        }
                        if (parentInShaderReadOnly) {
                            VulkanImages.vkTransitionImageLayout(
                                commandBuffer,
                                parentTarget.colorImage,
                                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                                VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                            )
                            parentInShaderReadOnly = false
                        }
                        beginParentRenderPass(commandBuffer, parentTarget, desiredRenderPass)
                        parentPassOpen = true
                        currentParentRenderPass = desiredRenderPass
                    }
                    applyScissor(commandBuffer, destination.scissorRect, parentTarget.width, parentTarget.height)
                    recordStepDraw(
                        commandBuffer = commandBuffer,
                        frameIndex = safeFrame,
                        step = step,
                        renderPass = currentParentRenderPass,
                        blendEnabled = destination.blendEnabled,
                        blendMode = destination.blendMode,
                        isPremultiplied = destination.isPremultiplied,
                        parentTarget = parentTarget,
                        activeTransients = activeTransients,
                        stats = stats,
                    )
                }
            }
        }

        val finalResumeRenderPass = resolveParentResumeRenderPass(parentTarget, hasMoreImagePasses)
        if (!parentPassOpen || currentParentRenderPass != finalResumeRenderPass) {
            if (parentPassOpen) {
                Vulkan.vkCmdEndRenderPass(commandBuffer)
            }
            if (parentInShaderReadOnly) {
                VulkanImages.vkTransitionImageLayout(
                    commandBuffer,
                    parentTarget.colorImage,
                    VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                )
            }
            beginParentRenderPass(commandBuffer, parentTarget, finalResumeRenderPass)
        } else {
            Vulkan.vkCmdSetViewport(
                commandBuffer,
                0,
                arrayOf(VkViewport(width = parentTarget.width.toFloat(), height = parentTarget.height.toFloat())),
            )
            Vulkan.vkCmdSetScissor(
                commandBuffer,
                0,
                arrayOf(VkRect2D(extent = VkExtent2D(parentTarget.width, parentTarget.height))),
            )
        }
    }

    /** Releases all pooled transient targets while keeping compiled shaders and pipelines intact. */
    fun releaseTransientTargets() {
        transientTargets.values.forEach { it.destroy(device) }
        transientTargets.clear()
    }

    /** Frees all pooled transient targets, descriptors, samplers, shaders, and pipelines. */
    fun destroy() {
        releaseTransientTargets()
        stepSlotsByFrame.forEach { byShader ->
            byShader.values.forEach { slots ->
                slots.forEach { slot -> slot.destroy(device) }
            }
            byShader.clear()
        }
        stepSlotsUsedByFrame.forEach { it.clear() }
        pipelines.values.forEach { Vulkan.vkDestroyPipeline(device, it) }
        pipelines.clear()
        compiledShaders.values.forEach { it.destroy(device) }
        compiledShaders.clear()
        samplers.values.forEach { VulkanImages.vkDestroySampler(device, it) }
        samplers.clear()
        transientClearRenderPasses.values.forEach { Vulkan.vkDestroyRenderPass(device, it) }
        transientClearRenderPasses.clear()
        transientLoadRenderPasses.values.forEach { Vulkan.vkDestroyRenderPass(device, it) }
        transientLoadRenderPasses.clear()
        swapchainContinueRenderPasses.values.forEach { Vulkan.vkDestroyRenderPass(device, it) }
        swapchainContinueRenderPasses.clear()
        offscreenContinueRenderPasses.values.forEach { Vulkan.vkDestroyRenderPass(device, it) }
        offscreenContinueRenderPasses.clear()
        offscreenClearRenderPasses.values.forEach { Vulkan.vkDestroyRenderPass(device, it) }
        offscreenClearRenderPasses.clear()
    }

    private fun resolveParentResumeRenderPass(
        parentTarget: VulkanImagePassParentTarget,
        willInterruptAgain: Boolean,
    ): Long = when {
        parentTarget.isOffscreen -> ensureOffscreenContinueRenderPass(parentTarget.colorFormat)
        willInterruptAgain -> ensureSwapchainContinueRenderPass(parentTarget.colorFormat)
        else -> parentTarget.finalRenderPass
    }

    private fun beginParentRenderPass(
        commandBuffer: Long,
        parentTarget: VulkanImagePassParentTarget,
        renderPass: Long,
    ) {
        // The resumed pass loads what the interrupted one wrote; its dependency must match the
        // original pass for compatibility, so the ordering is recorded here explicitly.
        VulkanImages.vkCmdMemoryBarrier(
            commandBuffer,
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
            VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
            VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT.value or
                VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
        )
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = renderPass,
                framebuffer = parentTarget.framebuffer,
                renderArea = VkRect2D(extent = VkExtent2D(parentTarget.width, parentTarget.height)),
            ),
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        Vulkan.vkCmdSetViewport(
            commandBuffer,
            0,
            arrayOf(VkViewport(width = parentTarget.width.toFloat(), height = parentTarget.height.toFloat())),
        )
        Vulkan.vkCmdSetScissor(
            commandBuffer,
            0,
            arrayOf(VkRect2D(extent = VkExtent2D(parentTarget.width, parentTarget.height))),
        )
    }

    private fun applyScissor(
        commandBuffer: Long,
        scissorRect: ImagePassRect?,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        if (scissorRect == null) {
            Vulkan.vkCmdSetScissor(
                commandBuffer,
                0,
                arrayOf(VkRect2D(extent = VkExtent2D(targetWidth, targetHeight))),
            )
            return
        }
        val clamped = scissorRect.clampTo(targetWidth.toFloat(), targetHeight.toFloat())
        val x = clamped.x.roundToInt().coerceIn(0, targetWidth)
        val y = clamped.y.roundToInt().coerceIn(0, targetHeight)
        val w = clamped.width.roundToInt().coerceAtLeast(0).coerceAtMost(targetWidth - x)
        val h = clamped.height.roundToInt().coerceAtLeast(0).coerceAtMost(targetHeight - y)
        Vulkan.vkCmdSetScissor(
            commandBuffer,
            0,
            arrayOf(VkRect2D(offset = VkOffset2D(x = x, y = y), extent = VkExtent2D(width = w, height = h))),
        )
    }

    @Suppress("LongParameterList")
    private fun recordStepDraw(
        commandBuffer: Long,
        frameIndex: Int,
        step: ImagePassStep,
        renderPass: Long,
        blendEnabled: Boolean,
        blendMode: BlendMode,
        isPremultiplied: Boolean,
        parentTarget: VulkanImagePassParentTarget,
        activeTransients: Map<String, PooledTransientTarget>,
        stats: RenderStatsCounter,
    ) {
        val compiled = ensureCompiledShader(step.shader)
        val pipeline = ensurePipeline(
            compiled = compiled,
            renderPass = renderPass,
            blendEnabled = blendEnabled,
            blendMode = blendMode,
            isPremultiplied = isPremultiplied,
        )
        val slot = acquireStepSlot(frameIndex, compiled)
        if (compiled.uniformByteSize > 0L && step.uniformData.isNotEmpty()) {
            VulkanBuffers.writeBufferMemoryFloats(device, slot.uniformMemory, 0, step.uniformData)
        }
        bindStepInputs(
            descriptorSet = slot.descriptorSet,
            groupBindings = compiled.spec.groupBindings,
            inputs = step.inputs,
            parentTarget = parentTarget,
            activeTransients = activeTransients,
        )
        Vulkan.vkCmdBindPipeline(
            commandBuffer,
            VkPipelineBindPoint.VK_PIPELINE_BIND_POINT_GRAPHICS,
            pipeline,
        )
        VulkanDescriptors.vkCmdBindDescriptorSet(
            commandBuffer,
            compiled.pipelineLayout,
            0,
            slot.descriptorSet,
        )

        val vertices = if (step.hasCustomGeometry) step.quadVertices else DEFAULT_FULLSCREEN_QUAD_VERTICES
        val indices = if (step.hasCustomGeometry) {
            IntArray(step.quadIndices.size) { index -> step.quadIndices[index].toInt() and USHORT_MASK }
        } else {
            DEFAULT_FULLSCREEN_QUAD_INDICES
        }
        val meshIndex = meshStepsUsedByFrame[frameIndex]
        meshStepsUsedByFrame[frameIndex] = meshIndex + 1
        val mesh = meshProvider(meshIndex)
        mesh.update(frameIndex, vertices, indices)
        mesh.bind(frameIndex, commandBuffer)
        mesh.draw(frameIndex, commandBuffer, stats)
    }

    private fun bindStepInputs(
        descriptorSet: Long,
        groupBindings: GroupBindings,
        inputs: List<ImagePassTextureBinding>,
        parentTarget: VulkanImagePassParentTarget,
        activeTransients: Map<String, PooledTransientTarget>,
    ) {
        val samplerEntries = groupBindings.entries.filter { it.kind == ResourceKind.Sampler }
        for (index in inputs.indices) {
            val input = inputs[index]
            val imageView = when (val source = input.source) {
                ImagePassSource.DestinationSnapshot -> parentTarget.colorImageView
                is ImagePassSource.TransientTarget ->
                    checkNotNull(activeTransients[source.id]) {
                        "ImagePassStep references unknown transient target '${source.id}'."
                    }.imageView
                is ImagePassSource.RenderTargetColor ->
                    (source.target as OffscreenRenderTarget).colorImageView
                is ImagePassSource.Texture ->
                    textureProvider(source.texture).imageView.handle
            }
            VulkanDescriptors.vkUpdateDescriptorSetImage(
                device,
                descriptorSet,
                input.binding,
                VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE,
                VkDescriptorImageInfo(sampler = 0, imageView = imageView),
            )
            val samplerBinding = samplerEntries.firstOrNull { it.binding == input.binding + 1 }?.binding
                ?: samplerEntries.getOrNull(index)?.binding
            if (samplerBinding != null) {
                val sampler = ensureSampler(input.sampler)
                VulkanDescriptors.vkUpdateDescriptorSetImage(
                    device,
                    descriptorSet,
                    samplerBinding,
                    VkDescriptorType.VK_DESCRIPTOR_TYPE_SAMPLER,
                    VkDescriptorImageInfo(sampler = sampler, imageView = 0),
                )
            }
        }
    }

    private fun acquireTransientTarget(
        spec: ImagePassTargetSpec,
        destinationFormat: VkFormat,
        referenceWidth: Int,
        referenceHeight: Int,
    ): PooledTransientTarget {
        val width = spec.scaledWidth(referenceWidth)
        val height = spec.scaledHeight(referenceHeight)
        val vkFormat = when (spec.format) {
            ImagePassTargetFormat.DestinationMatch -> destinationFormat
            ImagePassTargetFormat.Rgba8Unorm -> VkFormat.VK_FORMAT_R8G8B8A8_UNORM
            ImagePassTargetFormat.Rgba16Float -> VkFormat.VK_FORMAT_R16G16B16A16_SFLOAT
        }
        val existing = transientTargets[spec.id]
        if (existing != null) {
            if (existing.width == width && existing.height == height && existing.format == vkFormat) {
                totalTargetReuses += 1
                return existing
            }
            existing.destroy(device)
        }
        totalTargetAllocations += 1
        val created = createTransientTarget(width, height, vkFormat)
        transientTargets[spec.id] = created
        return created
    }

    @Suppress("LongMethod")
    private fun createTransientTarget(
        width: Int,
        height: Int,
        format: VkFormat,
    ): PooledTransientTarget {
        val image = VulkanImages.vkCreateImage(
            device,
            VkImageCreateInfo(
                width = width,
                height = height,
                format = format.value,
                usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or
                    VkImageUsageFlagBits2.VK_IMAGE_USAGE_SAMPLED_BIT or
                    VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
            ),
        )
        val requirements = VulkanImages.vkGetImageMemoryRequirements(device, image)
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
        )
        val memory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
        )
        VulkanImages.vkBindImageMemory(device, image, memory, 0)
        val imageView = Vulkan.vkCreateImageView(
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
        val clearRenderPass = ensureTransientClearRenderPass(format)
        val loadRenderPass = ensureTransientLoadRenderPass(format)
        val clearFramebuffer = Vulkan.vkCreateFramebuffer(
            device,
            VkFramebufferCreateInfo(
                renderPass = clearRenderPass,
                pAttachments = arrayOf(imageView),
                width = width,
                height = height,
                layers = 1,
            ),
        )
        val loadFramebuffer = Vulkan.vkCreateFramebuffer(
            device,
            VkFramebufferCreateInfo(
                renderPass = loadRenderPass,
                pAttachments = arrayOf(imageView),
                width = width,
                height = height,
                layers = 1,
            ),
        )
        return PooledTransientTarget(
            width = width,
            height = height,
            format = format,
            image = image,
            memory = memory,
            imageView = imageView,
            clearFramebuffer = clearFramebuffer,
            loadFramebuffer = loadFramebuffer,
        )
    }

    private fun ensureTransientClearRenderPass(colorFormat: VkFormat): Long =
        transientClearRenderPasses.getOrPut(colorFormat) {
            createSingleColorRenderPass(
                colorFormat = colorFormat,
                loadOp = VkAttachmentLoadOp.CLEAR,
                initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
                finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            )
        }

    private fun ensureTransientLoadRenderPass(colorFormat: VkFormat): Long =
        transientLoadRenderPasses.getOrPut(colorFormat) {
            createSingleColorRenderPass(
                colorFormat = colorFormat,
                loadOp = VkAttachmentLoadOp.LOAD,
                initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            )
        }

    @Suppress("LongParameterList")
    private fun createSingleColorRenderPass(
        colorFormat: VkFormat,
        loadOp: VkAttachmentLoadOp,
        initialLayout: VkImageLayout,
        finalLayout: VkImageLayout,
        srcStageMask: Int = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value or
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
        srcAccessMask: Int = VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value or
            VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
    ): Long = Vulkan.vkCreateRenderPass(
        device,
        VkRenderPassCreateInfo(
            pAttachments = arrayOf(
                VkAttachmentDescription(
                    format = colorFormat,
                    loadOp = loadOp,
                    storeOp = VkAttachmentStoreOp.STORE,
                    initialLayout = initialLayout,
                    finalLayout = finalLayout,
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
                    srcStageMask = srcStageMask,
                    srcAccessMask = srcAccessMask,
                    dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
                    dstAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT.value or
                        VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value,
                ),
            ),
        ),
    )

    /** Post-processing and UI resume on resolved colour; scene depth is no longer attached. */
    private fun createOffscreenContinueRenderPass(colorFormat: VkFormat): Long = createSingleColorRenderPass(
        colorFormat = colorFormat,
        loadOp = VkAttachmentLoadOp.LOAD,
        initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
        finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
    )

    /** Single-sample clear pass for an offscreen UI layer, compatible with its continuation pass. */
    fun ensureOffscreenClearRenderPass(colorFormat: VkFormat): Long = offscreenClearRenderPasses.getOrPut(colorFormat) {
        createSingleColorRenderPass(
            colorFormat = colorFormat,
            loadOp = VkAttachmentLoadOp.CLEAR,
            initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
            finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
        )
    }

    private fun ensureSampler(spec: ImagePassSamplerSpec): Long = samplers.getOrPut(spec) {
        val filter = when (spec.filter) {
            ImagePassFilterMode.Nearest -> VkFilter.VK_FILTER_NEAREST
            ImagePassFilterMode.Linear -> VkFilter.VK_FILTER_LINEAR
        }
        val addressMode = when (spec.addressMode) {
            ImagePassAddressMode.ClampToEdge -> VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
            ImagePassAddressMode.Repeat -> VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT
        }
        VulkanImages.vkCreateSampler(
            device,
            VkSamplerCreateInfo(
                magFilter = filter,
                minFilter = filter,
                addressModeU = addressMode,
                addressModeV = addressMode,
                addressModeW = addressMode,
                mipmapMode = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST,
            ),
        )
    }

    private fun ensureCompiledShader(spec: ImagePassShaderSpec): CompiledShader =
        compiledShaders.getOrPut(spec.id) {
            val spirvWords = NagaShaderCompiler.wgslToSpirv(spec.wgslSource).toShaderIntArray()
            val shaderModule = createShaderModule(device, spirvWords)
            val descriptorSetLayout = VulkanDescriptors.vkCreateDescriptorSetLayout(
                device,
                VkDescriptorSetLayoutCreateInfo(
                    pBindings = materialLayoutBindings(spec.groupBindings).toTypedArray(),
                ),
            )
            val pipelineLayout = Vulkan.vkCreatePipelineLayout(
                device,
                VkPipelineLayoutCreateInfo(
                    pSetLayouts = arrayOf(descriptorSetLayout),
                    pPushConstantRanges = emptyArray(),
                ),
            )
            val pipelineCache = Vulkan.vkCreatePipelineCache(device, VkPipelineCacheCreateInfo())
            val uniformByteSize = ((spec.uniformLayout?.total ?: 0) * Float.SIZE_BYTES).toLong()
            val uniformBinding = spec.groupBindings.entries
                .firstOrNull { it.kind == ResourceKind.UniformBuffer }
                ?.binding
            CompiledShader(
                spec = spec,
                shaderModule = shaderModule,
                descriptorSetLayout = descriptorSetLayout,
                pipelineLayout = pipelineLayout,
                pipelineCache = pipelineCache,
                uniformByteSize = uniformByteSize,
                uniformBinding = uniformBinding,
            )
        }

    private fun ensurePipeline(
        compiled: CompiledShader,
        renderPass: Long,
        blendEnabled: Boolean,
        blendMode: BlendMode,
        isPremultiplied: Boolean,
    ): Long {
        val key = PipelineKey(
            shaderId = compiled.spec.id,
            renderPass = renderPass,
            blendEnabled = blendEnabled,
            blendMode = blendMode,
            isPremultiplied = isPremultiplied,
        )
        return pipelines.getOrPut(key) {
            buildGraphicsPipeline(
                compiled = compiled,
                renderPass = renderPass,
                blendEnabled = blendEnabled,
                blendMode = blendMode,
                isPremultiplied = isPremultiplied,
            )
        }
    }

    @Suppress("LongMethod")
    private fun buildGraphicsPipeline(
        compiled: CompiledShader,
        renderPass: Long,
        blendEnabled: Boolean,
        blendMode: BlendMode,
        isPremultiplied: Boolean,
    ): Long {
        val shaderStages = arrayOf(
            VkPipelineShaderStageCreateInfo(
                stage = VkShaderStageFlagBits.FRAGMENT,
                module = compiled.shaderModule,
                pName = FRAGMENT_ENTRY_POINT,
            ),
            VkPipelineShaderStageCreateInfo(
                stage = VkShaderStageFlagBits.VERTEX,
                module = compiled.shaderModule,
                pName = VERTEX_ENTRY_POINT,
            ),
        )
        val vertexInputInfo = buildVertexInputState(compiled.spec.vertexFormat)
        val inputAssembly = arrayOf(
            VkPipelineInputAssemblyStateCreateInfo(
                topology = VkPrimitiveTopology.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                primitiveRestartEnable = false,
            ),
        )
        val viewportState = arrayOf(
            VkPipelineViewportStateCreateInfo(
                pViewports = arrayOf(VkViewport(width = 1f, height = 1f)),
                pScissors = arrayOf(VkRect2D(extent = VkExtent2D(width = 1, height = 1))),
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
        val srcColorFactor = when (blendMode) {
            BlendMode.SourceOver, BlendMode.Plus ->
                if (isPremultiplied) VkBlendFactor.VK_BLEND_FACTOR_ONE else VkBlendFactor.VK_BLEND_FACTOR_SRC_ALPHA
            BlendMode.Screen, BlendMode.Overlay -> VkBlendFactor.VK_BLEND_FACTOR_ONE
        }
        val dstColorFactor = when (blendMode) {
            BlendMode.SourceOver -> VkBlendFactor.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
            BlendMode.Plus, BlendMode.Screen, BlendMode.Overlay -> VkBlendFactor.VK_BLEND_FACTOR_ONE
        }
        val colorBlendAttachment = arrayOf(
            VkPipelineColorBlendAttachmentState(
                colorWriteMask = VkColorComponentFlagBits.VK_COLOR_COMPONENT_R_BIT.value or
                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_G_BIT.value or
                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_B_BIT.value or
                    VkColorComponentFlagBits.VK_COLOR_COMPONENT_A_BIT.value,
                blendEnable = blendEnabled,
                srcColorBlendFactor = srcColorFactor,
                dstColorBlendFactor = dstColorFactor,
                colorBlendOp = VkBlendOp.VK_BLEND_OP_ADD,
                srcAlphaBlendFactor = VkBlendFactor.VK_BLEND_FACTOR_ONE,
                dstAlphaBlendFactor = dstColorFactor,
                alphaBlendOp = VkBlendOp.VK_BLEND_OP_ADD,
            ),
        )
        val colorBlending = arrayOf(
            VkPipelineColorBlendStateCreateInfo(
                logicOpEnable = false,
                pAttachments = colorBlendAttachment,
            ),
        )
        val dynamicStateInfo = arrayOf(
            VkPipelineDynamicStateCreateInfo(
                pDynamicStates = arrayOf(
                    VkDynamicState.VK_DYNAMIC_STATE_VIEWPORT,
                    VkDynamicState.VK_DYNAMIC_STATE_SCISSOR,
                ),
            ),
        )
        val depthStencil = arrayOf(
            VkPipelineDepthStencilStateCreateInfo(
                depthTestEnable = false,
                depthWriteEnable = false,
                depthCompareOp = VkCompareOp.VK_COMPARE_OP_NEVER,
                depthBoundsTestEnable = false,
                stencilTestEnable = false,
            ),
        )
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
                layout = compiled.pipelineLayout,
                renderPass = renderPass,
                subpass = 0,
            ),
        )
        return Vulkan.vkCreateGraphicsPipelines(device, compiled.pipelineCache, pipelineInfo)[0]
    }

    private fun buildVertexInputState(format: VertexFormat): Array<VkPipelineVertexInputStateCreateInfo> {
        if (format.attributes.isEmpty()) {
            return arrayOf(
                VkPipelineVertexInputStateCreateInfo(
                    pVertexBindingDescriptions = emptyArray(),
                    pVertexAttributeDescriptions = emptyArray(),
                ),
            )
        }
        val attributes = format.entries.map { entry ->
            VkVertexInputAttributeDescription(
                location = entry.attribute.location,
                binding = 0,
                format = entry.attribute.format.toVkFormat(),
                offset = entry.offsetBytes,
            )
        }.toTypedArray()
        return arrayOf(
            VkPipelineVertexInputStateCreateInfo(
                pVertexBindingDescriptions = arrayOf(
                    VkVertexInputBindingDescription(
                        binding = 0,
                        stride = format.strideBytes,
                        inputRate = VkVertexInputRate.VK_VERTEX_INPUT_RATE_VERTEX,
                    ),
                ),
                pVertexAttributeDescriptions = attributes,
            ),
        )
    }

    private fun acquireStepSlot(frameIndex: Int, compiled: CompiledShader): StepDescriptorSlot {
        val slotsByShader = stepSlotsByFrame[frameIndex]
        val usedByShader = stepSlotsUsedByFrame[frameIndex]
        val slots = slotsByShader.getOrPut(compiled.spec.id) { mutableListOf() }
        val slotIndex = usedByShader[compiled.spec.id] ?: 0
        usedByShader[compiled.spec.id] = slotIndex + 1
        while (slots.size <= slotIndex) {
            slots.add(createStepDescriptorSlot(compiled))
        }
        return slots[slotIndex]
    }

    private fun createStepDescriptorSlot(compiled: CompiledShader): StepDescriptorSlot {
        val pool = VulkanDescriptors.vkCreateDescriptorPool(
            device,
            VkDescriptorPoolCreateInfo(
                maxSets = 1,
                pPoolSizes = materialPoolSizes(compiled.spec.groupBindings).toTypedArray(),
            ),
        )
        val set = VulkanDescriptors.vkAllocateDescriptorSet(device, pool, compiled.descriptorSetLayout)
        var uniformBuffer = 0L
        var uniformMemory = 0L
        if (compiled.uniformByteSize > 0L && compiled.uniformBinding != null) {
            val buffer = VulkanBuffers.vkCreateBuffer(
                device,
                VkBufferCreateInfo(
                    size = compiled.uniformByteSize,
                    usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                ),
            )
            val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
            val memoryTypeIndex = VulkanBuffers.findMemoryType(
                physicalDevice,
                requirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            )
            val memory = VulkanBuffers.vkAllocateMemory(
                device,
                VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
            )
            VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
            VulkanDescriptors.vkUpdateDescriptorSetBuffer(
                device,
                set,
                compiled.uniformBinding,
                VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                VkDescriptorBufferInfo(buffer = buffer, range = compiled.uniformByteSize),
            )
            uniformBuffer = buffer
            uniformMemory = memory
        }
        return StepDescriptorSlot(
            descriptorPool = pool,
            descriptorSet = set,
            uniformBuffer = uniformBuffer,
            uniformMemory = uniformMemory,
        )
    }

    @Suppress("LongParameterList")
    private class PooledTransientTarget(
        val width: Int,
        val height: Int,
        val format: VkFormat,
        val image: Long,
        val memory: Long,
        val imageView: Long,
        val clearFramebuffer: Long,
        val loadFramebuffer: Long,
        var inShaderReadOnly: Boolean = false,
    ) {
        fun destroy(device: Long) {
            Vulkan.vkDestroyFramebuffer(device, clearFramebuffer)
            Vulkan.vkDestroyFramebuffer(device, loadFramebuffer)
            Vulkan.vkDestroyImageView(device, imageView)
            VulkanImages.vkDestroyImage(device, image)
            VulkanBuffers.vkFreeMemory(device, memory)
        }
    }

    @Suppress("LongParameterList")
    private class CompiledShader(
        val spec: ImagePassShaderSpec,
        val shaderModule: Long,
        val descriptorSetLayout: Long,
        val pipelineLayout: Long,
        val pipelineCache: Long,
        val uniformByteSize: Long,
        val uniformBinding: Int?,
    ) {
        fun destroy(device: Long) {
            Vulkan.vkDestroyShaderModule(device, shaderModule)
            Vulkan.vkDestroyPipelineLayout(device, pipelineLayout)
            Vulkan.vkDestroyPipelineCache(device, pipelineCache)
            VulkanDescriptors.vkDestroyDescriptorSetLayout(device, descriptorSetLayout)
        }
    }

    private data class PipelineKey(
        val shaderId: String,
        val renderPass: Long,
        val blendEnabled: Boolean,
        val blendMode: BlendMode,
        val isPremultiplied: Boolean,
    )

    private class StepDescriptorSlot(
        val descriptorPool: Long,
        val descriptorSet: Long,
        val uniformBuffer: Long,
        val uniformMemory: Long,
    ) {
        fun destroy(device: Long) {
            if (uniformBuffer != 0L) VulkanBuffers.vkDestroyBuffer(device, uniformBuffer)
            if (uniformMemory != 0L) VulkanBuffers.vkFreeMemory(device, uniformMemory)
            VulkanDescriptors.vkDestroyDescriptorPool(device, descriptorPool)
        }
    }

    private companion object {
        const val VERTEX_ENTRY_POINT = "vertexMain"
        const val FRAGMENT_ENTRY_POINT = "fragmentMain"
        const val USHORT_MASK = 0xFFFF

        val DEFAULT_FULLSCREEN_QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            1f, 1f, 1f, 1f,
            -1f, 1f, 0f, 1f,
        )
        val DEFAULT_FULLSCREEN_QUAD_INDICES = intArrayOf(0, 1, 2, 0, 2, 3)
    }
}
