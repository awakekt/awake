/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes2d.DrawRun
import com.awakekt.awake.render.renderer.RenderScale
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.enums.flags.VkCommandBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkCommandBufferBeginInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.pipeline.VulkanImagePassParentTarget
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget

/** Records the Vulkan swapchain scene pass and its UI or present-transition pass. */
@Suppress("LongParameterList", "LongMethod")
internal fun Renderer.recordCommandBuffer(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    drawCalls: List<PreparedDrawCall>,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    cascades: GpuShadowCascadeData? = null,
    environment: GpuEnvironmentState = GpuEnvironmentState.Default,
    sceneViewport: RenderViewport? = null,
) {
    Vulkan.vkBeginCommandBuffer(
        commandBuffer,
        VkCommandBufferBeginInfo(
            flags = VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
        ),
    )
    // Before the scene pass begins, in the same buffer: its fragment shader samples the depth this
    // writes, and DepthTarget's outgoing subpass dependency orders the two on the GPU. This used
    // to be a separate submit the CPU blocked on.
    prepareResources(frameIndex, commandBuffer)
    recordDepthPrePass(commandBuffer, frameIndex, drawCalls, cascades, true)
    // The camera's own depth, expressed as the one "cascade" this frame renders from the eye.
    recordSceneDepthPass(commandBuffer, frameIndex, drawCalls, cameraDepthPass(viewProjection))
    val outputWidth = swapchainManager.extent.width
    val outputHeight = swapchainManager.extent.height
    val scaled = sceneScale.resolve(outputWidth, outputHeight, renderScale)
    scaled?.prepareForColorAttachment(commandBuffer)
    val sceneExtent = if (scaled != null) VkExtent2D(scaled.width, scaled.height) else swapchainManager.extent
    statsCounter.recordPass()
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = scaled?.framebuffer ?: framebuffers[acquiredImageIndex],
            renderArea = VkRect2D(extent = sceneExtent),
            pClearValues = arrayOf(clearColorValue(environment), Renderer.clearDepthValue),
        ),
        VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
    )

    val sorted = sortForRecording(drawCalls)
    val context = VulkanFrameContext(
        renderer = this,
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        groupedDrawCalls = sorted.opaqueByPipeline,
        transparentDrawCalls = sorted.transparent,
        primaryPipeline = pipelineFor(renderPipeline.vertexFormat) ?: renderPipeline,
        viewProjection = viewProjection,
        cameraEye = cameraEye,
        environment = environment,
        acquiredImageIndex = acquiredImageIndex,
    )
    val viewport = VkViewport(
        width = swapchainManager.extent.width.toFloat(),
        height = swapchainManager.extent.height.toFloat(),
    )
    val scissor = VkRect2D(extent = swapchainManager.extent)
    val sceneFullViewport = VkViewport(
        width = sceneExtent.width.toFloat(),
        height = sceneExtent.height.toFloat(),
    )
    val sceneFullScissor = VkRect2D(extent = sceneExtent)
    val effectiveSceneViewport = if (scaled != null && sceneViewport != null) {
        RenderScale.sceneViewport(sceneViewport, outputWidth, outputHeight, scaled.width, scaled.height)
    } else {
        sceneViewport
    }
    val sceneRect = effectiveSceneViewport?.clampedTo(
        sceneExtent.width.toFloat(),
        sceneExtent.height.toFloat(),
    )
    Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(sceneRect?.toVkViewport() ?: sceneFullViewport))
    Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(sceneRect?.toVkScissor() ?: sceneFullScissor))
    bindDepthSets(commandBuffer)
    recordSharedPassFeatures(RenderPassSlot.Scene, context)
    Vulkan.vkCmdEndRenderPass(commandBuffer)
    if (scaled != null) {
        scaled.transitionToShaderReadOnly(commandBuffer)
        recordSwapchainPostPasses(
            commandBuffer = commandBuffer,
            frameIndex = frameIndex,
            acquiredImageIndex = acquiredImageIndex,
            chain = sceneScale.upscale(scaled, outputWidth, outputHeight),
            clearDestination = true,
            environment = environment,
        )
    }

    recordUiOrPresentTransitionPass(
        commandBuffer = commandBuffer,
        acquiredImageIndex = acquiredImageIndex,
        viewport = viewport,
        scissor = scissor,
        context = context,
    )
    Vulkan.vkEndCommandBuffer(commandBuffer)
}

/** Records a packet whose handles were resolved by the shared compiler. Resolved packets do not
 * enter the legacy depth-feature adapter yet; they still use the same shared scene/UI recorder. */
@Suppress("LongParameterList", "LongMethod")
internal fun Renderer.recordResolvedCommandBuffer(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    drawCalls: List<PreparedDraw>,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    depthDraws: List<PreparedDraw> = emptyList(),
    prePasses: List<GpuSubPass> = emptyList(),
    postPasses: List<GpuSubPass> = emptyList(),
    environment: GpuEnvironmentState = GpuEnvironmentState.Default,
    sceneViewport: RenderViewport? = null,
    passInput: GpuPassInput? = null,
) {
    Vulkan.vkBeginCommandBuffer(
        commandBuffer,
        VkCommandBufferBeginInfo(
            flags = VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
        ),
    )
    gpuFrameTimer?.begin(commandBuffer, frameIndex)
    prepareResources(frameIndex, commandBuffer)
    recordDepthPrePass(commandBuffer, frameIndex, depthDraws, prePasses, environment)
    recordSceneDepthPass(commandBuffer, frameIndex, depthDraws, cameraDepthPass(viewProjection))
    recordMaskPass(commandBuffer, frameIndex, passInput?.maskPasses.orEmpty(), environment)
    val outputWidth = swapchainManager.extent.width
    val outputHeight = swapchainManager.extent.height
    val scaled = sceneScale.resolve(outputWidth, outputHeight, renderScale)
    scaled?.prepareForColorAttachment(commandBuffer)
    val sceneExtent = if (scaled != null) VkExtent2D(scaled.width, scaled.height) else swapchainManager.extent
    val scenePassInput = if (scaled != null && passInput != null) {
        sceneScale.sceneInput(passInput, scaled, outputWidth, outputHeight)
    } else {
        passInput
    }
    statsCounter.recordPass()
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = scaled?.framebuffer ?: framebuffers[acquiredImageIndex],
            renderArea = VkRect2D(extent = sceneExtent),
            pClearValues = arrayOf(clearColorValue(environment), Renderer.clearDepthValue),
        ),
        VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
    )
    val sorted = sortForRecording(drawCalls)
    val context = VulkanFrameContext(
        renderer = this,
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        groupedDrawCalls = sorted.opaqueByPipeline,
        transparentDrawCalls = sorted.transparent,
        primaryPipeline = pipelineFor(renderPipeline.vertexFormat) ?: renderPipeline,
        viewProjection = viewProjection,
        cameraEye = cameraEye,
        environment = environment,
        passInput = scenePassInput,
        acquiredImageIndex = acquiredImageIndex,
    )
    val viewport = VkViewport(
        width = swapchainManager.extent.width.toFloat(),
        height = swapchainManager.extent.height.toFloat(),
    )
    val scissor = VkRect2D(extent = swapchainManager.extent)
    val sceneFullViewport = VkViewport(
        width = sceneExtent.width.toFloat(),
        height = sceneExtent.height.toFloat(),
    )
    val sceneFullScissor = VkRect2D(extent = sceneExtent)
    val effectiveSceneViewport = if (scaled != null && sceneViewport != null) {
        RenderScale.sceneViewport(sceneViewport, outputWidth, outputHeight, scaled.width, scaled.height)
    } else {
        sceneViewport
    }
    val sceneRect = effectiveSceneViewport?.clampedTo(
        sceneExtent.width.toFloat(),
        sceneExtent.height.toFloat(),
    )
    Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(sceneRect?.toVkViewport() ?: sceneFullViewport))
    Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(sceneRect?.toVkScissor() ?: sceneFullScissor))
    bindDepthSets(commandBuffer)
    recordSharedPassFeatures(RenderPassSlot.Scene, context)
    Vulkan.vkCmdEndRenderPass(commandBuffer)

    recordPostPasses(commandBuffer, postPasses)
    if (scaled != null) {
        if (scenePassInput != null) {
            recordPostPassesToTexture(commandBuffer, scaled, scenePassInput, frameIndex)
        }
        scaled.transitionToShaderReadOnly(commandBuffer)
        recordSwapchainPostPasses(
            commandBuffer = commandBuffer,
            frameIndex = frameIndex,
            acquiredImageIndex = acquiredImageIndex,
            chain = sceneScale.upscale(scaled, outputWidth, outputHeight),
            clearDestination = true,
            environment = environment,
        )
    } else {
        recordSwapchainPostPasses(
            commandBuffer = commandBuffer,
            frameIndex = frameIndex,
            acquiredImageIndex = acquiredImageIndex,
            chain = passInput?.postPassChain ?: ImagePassChain.Empty,
        )
    }

    recordUiOrPresentTransitionPass(
        commandBuffer = commandBuffer,
        acquiredImageIndex = acquiredImageIndex,
        viewport = viewport,
        scissor = scissor,
        context = context,
    )
    gpuFrameTimer?.end(commandBuffer, frameIndex)
    Vulkan.vkEndCommandBuffer(commandBuffer)
}

private fun Renderer.recordUiOrPresentTransitionPass(
    commandBuffer: Long,
    acquiredImageIndex: Int,
    viewport: VkViewport,
    scissor: VkRect2D,
    context: VulkanFrameContext,
) {
    val uiPipeline = uiRenderPipeline
    if (uiPipeline != null) {
        val hasImagePass = uiRuns.any { it is DrawRun.ImagePassRun }
        val initialRenderPass = if (hasImagePass) {
            imagePassExecutor.ensureSwapchainContinueRenderPass(swapchainManager.imageFormat)
        } else {
            uiPipeline.renderPass
        }
        statsCounter.recordPass()
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = initialRenderPass,
                framebuffer = uiFramebuffers[acquiredImageIndex],
                renderArea = VkRect2D(extent = swapchainManager.extent),
            ),
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(viewport))
        Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(scissor))
        recordSharedPassFeatures(RenderPassSlot.Ui, context)
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    } else {
        statsCounter.recordPass()
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = presentTransitionRenderPass,
                framebuffer = presentTransitionFramebuffers[acquiredImageIndex],
                renderArea = VkRect2D(extent = swapchainManager.extent),
            ),
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    }
}

@Suppress("ReturnCount", "LongParameterList")
internal fun Renderer.recordSwapchainPostPasses(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    chain: ImagePassChain,
    clearDestination: Boolean = false,
    environment: GpuEnvironmentState = GpuEnvironmentState.Default,
) {
    if (chain.isEmpty) return
    val swapchain = swapchainManager
    val colorImage = swapchain.images.getOrNull(acquiredImageIndex)
    val colorImageView = swapchain.imageViews.getOrNull(acquiredImageIndex)
    val framebuffer = uiFramebuffers.getOrNull(acquiredImageIndex)
        ?: presentTransitionFramebuffers.getOrNull(acquiredImageIndex)
    if (colorImage == null || colorImageView == null || framebuffer == null) return
    val continuePass = imagePassExecutor.ensureSwapchainContinueRenderPass(swapchain.imageFormat)
    val initialPass = if (clearDestination) {
        imagePassExecutor.ensureSwapchainClearRenderPass(swapchain.imageFormat)
    } else {
        continuePass
    }
    statsCounter.recordPass()
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = initialPass,
            framebuffer = framebuffer,
            renderArea = VkRect2D(extent = swapchain.extent),
            pClearValues = if (clearDestination) arrayOf(clearColorValue(environment)) else emptyArray(),
        ),
        VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
    )
    val parentTarget = VulkanImagePassParentTarget(
        framebuffer = framebuffer,
        colorImage = colorImage,
        colorImageView = colorImageView,
        colorFormat = swapchain.imageFormat,
        width = swapchain.extent.width,
        height = swapchain.extent.height,
        isOffscreen = false,
        finalRenderPass = continuePass,
        sampleable = swapchain.sampleable,
    )
    imagePassExecutor.executeImagePass(
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        parentTarget = parentTarget,
        chain = chain,
        hasMoreImagePasses = false,
        stats = statsCounter,
    )
    Vulkan.vkCmdEndRenderPass(commandBuffer)
}

/** The camera's view-projection as a one-entry cascade set -- what the scene-depth pass renders
 * from, through the same machinery the shadow pass uses. */
internal fun cameraDepthPass(viewProjection: Mat4): GpuShadowCascadeData =
    GpuShadowCascadeData(listOf(viewProjection), floatArrayOf(Float.MAX_VALUE))

@Suppress("UnusedParameter")
internal fun Renderer.recordPostPasses(
    commandBuffer: Long,
    postPasses: List<GpuSubPass>,
) {
    if (postPasses.isEmpty()) return
}

internal fun Renderer.recordPostPassesToTexture(
    commandBuffer: Long,
    target: OffscreenRenderTarget,
    input: GpuPassInput,
    frameIndex: Int = commandBuffers.size,
) {
    val chain = input.postPassChain
    if (!chain.isEmpty) {
        val swapchain = swapchainManager
        val continuePass = imagePassExecutor.ensureOffscreenContinueRenderPass(swapchain.imageFormat)
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = continuePass,
                framebuffer = target.colorFramebuffer(continuePass),
                renderArea = VkRect2D(extent = VkExtent2D(target.width, target.height)),
            ),
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        val parentTarget = VulkanImagePassParentTarget(
            framebuffer = target.colorFramebuffer(continuePass),
            colorImage = target.colorImage,
            colorImageView = target.colorImageView,
            colorFormat = swapchain.imageFormat,
            width = target.width,
            height = target.height,
            isOffscreen = true,
            finalRenderPass = continuePass,
            sampleable = true,
        )
        imagePassExecutor.executeImagePass(
            commandBuffer = commandBuffer,
            frameIndex = frameIndex,
            parentTarget = parentTarget,
            chain = chain,
            hasMoreImagePasses = false,
            stats = statsCounter,
        )
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    }
}
