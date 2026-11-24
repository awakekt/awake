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
import com.awakekt.awake.render.command.drawsForRecording
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes2d.DrawRun
import com.awakekt.awake.render.renderer.GpuFramePart
import com.awakekt.awake.render.renderer.RenderScale
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.flags.VkCommandBufferUsageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.pipeline.VulkanImagePassParentTarget
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import kotlin.math.ceil

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
    VulkanCommands.vkBeginCommandBuffer(
        commandBuffer,
        VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
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
    val sceneWidth = scaled?.width ?: outputWidth
    val sceneHeight = scaled?.height ?: outputHeight
    val clearRgba = environment.clearColorOr(clearColor)
    val hasUiDraws = uiRenderPipeline != null && uiRuns.isNotEmpty()
    val directPresentPass = if (scaled == null && !hasUiDraws) ensureSceneToPresentRenderPass() else 0L
    statsCounter.recordPass()
    VulkanCommands.vkCmdBeginRenderPassColorDepth(
        commandBuffer = commandBuffer,
        renderPass = if (directPresentPass != 0L) directPresentPass else renderPipeline.renderPass,
        framebuffer = scaled?.framebuffer ?: framebuffers[acquiredImageIndex],
        width = sceneWidth,
        height = sceneHeight,
        r = clearRgba.r,
        g = clearRgba.g,
        b = clearRgba.b,
        a = clearRgba.a,
        depth = 1f,
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
    setSceneViewportAndScissor(commandBuffer, scaled, sceneViewport, outputWidth, outputHeight, sceneWidth, sceneHeight)
    bindDepthSets(commandBuffer)
    recordSharedPassFeatures(RenderPassSlot.Scene, context.withSurfaceSize(sceneWidth, sceneHeight))
    Vulkan.vkCmdEndRenderPass(commandBuffer)
    if (scaled != null) {
        scaled.transitionToShaderReadOnly(commandBuffer)
        recordSwapchainPostPasses(
            commandBuffer = commandBuffer,
            frameIndex = frameIndex,
            acquiredImageIndex = acquiredImageIndex,
            chain = sceneScale.upscale(scaled, outputWidth, outputHeight, sceneUpscaling),
            clearDestination = true,
            environment = environment,
        )
    }

    if (directPresentPass == 0L) {
        recordUiOrPresentTransitionPass(
            commandBuffer = commandBuffer,
            acquiredImageIndex = acquiredImageIndex,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            context = context,
        )
    }
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
    VulkanCommands.vkBeginCommandBuffer(
        commandBuffer,
        VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
    )
    val timer = gpuFrameTimer?.takeIf { presentedFrameTiming }
    timer?.begin(commandBuffer, frameIndex, detailedParts = consumeDetailedPartTiming())
    prepareResources(frameIndex, commandBuffer)
    val shadowRan = recordDepthPrePass(commandBuffer, frameIndex, depthDraws, prePasses, environment)
    timer?.mark(commandBuffer, frameIndex, GpuFramePart.ShadowDepth, active = shadowRan)
    val sceneDepthRan = recordSceneDepthPass(commandBuffer, frameIndex, depthDraws, cameraDepthPass(viewProjection), environment)
    timer?.mark(commandBuffer, frameIndex, GpuFramePart.SceneDepth, active = sceneDepthRan)
    val maskRan = recordMaskPass(commandBuffer, frameIndex, passInput?.maskPasses.orEmpty(), environment)
    timer?.mark(commandBuffer, frameIndex, GpuFramePart.Mask, active = maskRan)
    val outputWidth = swapchainManager.extent.width
    val outputHeight = swapchainManager.extent.height
    val scaled = sceneScale.resolve(outputWidth, outputHeight, renderScale)
    scaled?.prepareForColorAttachment(commandBuffer)
    val sceneWidth = scaled?.width ?: outputWidth
    val sceneHeight = scaled?.height ?: outputHeight
    val scenePassInput = if (scaled != null && passInput != null) {
        sceneScale.sceneInput(passInput, scaled, outputWidth, outputHeight)
    } else {
        passInput
    }
    val clearRgba = environment.clearColorOr(clearColor)
    val postChain = passInput?.postPassChain ?: ImagePassChain.Empty
    val directPresentPass = resolveDirectPresentScenePass(scaled, postChain)
    statsCounter.recordPass()
    VulkanCommands.vkCmdBeginRenderPassColorDepth(
        commandBuffer = commandBuffer,
        renderPass = if (directPresentPass != 0L) directPresentPass else renderPipeline.renderPass,
        framebuffer = scaled?.framebuffer ?: framebuffers[acquiredImageIndex],
        width = sceneWidth,
        height = sceneHeight,
        r = clearRgba.r,
        g = clearRgba.g,
        b = clearRgba.b,
        a = clearRgba.a,
        depth = 1f,
    )
    val sorted = resolveSceneDraws(passInput, drawCalls)
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
    setSceneViewportAndScissor(commandBuffer, scaled, sceneViewport, outputWidth, outputHeight, sceneWidth, sceneHeight)
    bindDepthSets(commandBuffer)
    recordSharedPassFeatures(RenderPassSlot.Scene, context.withSurfaceSize(sceneWidth, sceneHeight))
    Vulkan.vkCmdEndRenderPass(commandBuffer)
    timer?.mark(commandBuffer, frameIndex, GpuFramePart.Scene)

    recordPostPasses(commandBuffer, postPasses)
    recordResolvedPostAndOutput(
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        acquiredImageIndex = acquiredImageIndex,
        scaled = scaled,
        postPasses = postPasses,
        scenePassInput = scenePassInput,
        postChain = postChain,
        outputWidth = outputWidth,
        outputHeight = outputHeight,
        context = context,
        environment = environment,
        directPresent = directPresentPass != 0L,
        timer = timer,
    )
    timer?.end(commandBuffer, frameIndex)
    Vulkan.vkEndCommandBuffer(commandBuffer)
}

@Suppress("LongParameterList")
private fun Renderer.recordResolvedPostAndOutput(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    scaled: OffscreenRenderTarget?,
    postPasses: List<GpuSubPass>,
    scenePassInput: GpuPassInput?,
    postChain: ImagePassChain,
    outputWidth: Int,
    outputHeight: Int,
    context: VulkanFrameContext,
    environment: GpuEnvironmentState,
    directPresent: Boolean,
    timer: GpuFrameTimer?,
) {
    // A scaled frame's upscale and UI share one pass on the swapchain image when they can (#1709).
    var presented = directPresent
    var upscaleRan = false
    if (scaled != null) {
        val postChainRan = scenePassInput != null && !scenePassInput.postPassChain.isEmpty
        if (scenePassInput != null) {
            recordPostPassesToTexture(commandBuffer, scaled, scenePassInput, frameIndex)
        }
        timer?.mark(commandBuffer, frameIndex, GpuFramePart.Post, active = postPasses.isNotEmpty() || postChainRan)
        scaled.transitionToShaderReadOnly(commandBuffer)
        val upscale = sceneScale.upscale(scaled, outputWidth, outputHeight, sceneUpscaling)
        upscaleRan = true
        presented = recordScaledOutput(commandBuffer, frameIndex, acquiredImageIndex, upscale, outputWidth, outputHeight, context, environment, timer)
    } else {
        // Unscaled, the frame's image passes draw on the output: they are the upscale part's.
        timer?.mark(commandBuffer, frameIndex, GpuFramePart.Post, active = postPasses.isNotEmpty())
        upscaleRan = !postChain.isEmpty
        recordSwapchainPostPasses(
            commandBuffer = commandBuffer,
            frameIndex = frameIndex,
            acquiredImageIndex = acquiredImageIndex,
            chain = postChain,
        )
    }
    if (!presented) {
        timer?.mark(commandBuffer, frameIndex, GpuFramePart.Upscale, active = upscaleRan)
        recordUiOrPresentTransitionPass(
            commandBuffer = commandBuffer,
            acquiredImageIndex = acquiredImageIndex,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            context = context,
        )
    } else if (directPresent) {
        timer?.mark(commandBuffer, frameIndex, GpuFramePart.Upscale, active = false)
    }
}

private fun Renderer.resolveDirectPresentScenePass(
    scaled: OffscreenRenderTarget?,
    postChain: ImagePassChain,
): Long {
    val hasUiDraws = uiRenderPipeline != null && uiRuns.isNotEmpty()
    return if (scaled == null && postChain.isEmpty && !hasUiDraws) {
        ensureSceneToPresentRenderPass()
    } else {
        0L
    }
}

@Suppress("LongParameterList")
private fun setSceneViewportAndScissor(
    commandBuffer: Long,
    scaled: OffscreenRenderTarget?,
    sceneViewport: RenderViewport?,
    outputWidth: Int,
    outputHeight: Int,
    sceneWidth: Int,
    sceneHeight: Int,
) {
    val effectiveSceneViewport = if (scaled != null && sceneViewport != null) {
        RenderScale.sceneViewport(sceneViewport, outputWidth, outputHeight, scaled.width, scaled.height)
    } else {
        sceneViewport
    }
    val sceneRect = effectiveSceneViewport?.clampedTo(
        sceneWidth.toFloat(),
        sceneHeight.toFloat(),
    )
    if (sceneRect != null) {
        VulkanCommands.vkCmdSetViewportRect(commandBuffer, sceneRect.x, sceneRect.y, sceneRect.width, sceneRect.height, 0f, 1f)
        VulkanCommands.vkCmdSetScissorRect(commandBuffer, sceneRect.x.toInt(), sceneRect.y.toInt(), ceil(sceneRect.width).toInt(), ceil(sceneRect.height).toInt())
    } else {
        VulkanCommands.vkCmdSetViewportRect(commandBuffer, 0f, 0f, sceneWidth.toFloat(), sceneHeight.toFloat(), 0f, 1f)
        VulkanCommands.vkCmdSetScissorRect(commandBuffer, 0, 0, sceneWidth, sceneHeight)
    }
}

private fun Renderer.recordUiOrPresentTransitionPass(
    commandBuffer: Long,
    acquiredImageIndex: Int,
    outputWidth: Int,
    outputHeight: Int,
    context: VulkanFrameContext,
) {
    val uiPipeline = uiRenderPipeline
    if (uiPipeline != null && uiRuns.isNotEmpty()) {
        val hasImagePass = uiRuns.any { it is DrawRun.ImagePassRun }
        val initialRenderPass = if (hasImagePass) {
            imagePassExecutor.ensureSwapchainContinueRenderPass(swapchainManager.imageFormat)
        } else {
            uiPipeline.renderPass
        }
        statsCounter.recordPass()
        VulkanCommands.vkCmdBeginRenderPassEmpty(
            commandBuffer,
            initialRenderPass,
            uiFramebuffers[acquiredImageIndex],
            outputWidth,
            outputHeight,
        )
        VulkanCommands.vkCmdSetViewportRect(commandBuffer, 0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), 0f, 1f)
        VulkanCommands.vkCmdSetScissorRect(commandBuffer, 0, 0, outputWidth, outputHeight)
        recordSharedPassFeatures(RenderPassSlot.Ui, context)
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    } else {
        // Transition COLOR_ATTACHMENT_OPTIMAL -> PRESENT_SRC_KHR with a pipeline barrier instead of
        // opening an empty LOAD+STORE render pass (avoids a full native-resolution DRAM round-trip
        // on tile-based mobile GPUs).
        val swapchainImage = swapchainManager.images.getOrNull(acquiredImageIndex) ?: return
        VulkanImages.vkTransitionImageLayout(
            commandBuffer,
            swapchainImage,
            VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            VkImageLayout2.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            levelCount = 1,
            layerCount = 1,
        )
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
        ?: ensureSwapchainColorFramebuffers().getOrNull(acquiredImageIndex)
    if (colorImage == null || colorImageView == null || framebuffer == null) return
    val continuePass = imagePassExecutor.ensureSwapchainContinueRenderPass(swapchain.imageFormat)
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
    if (clearDestination) {
        val clearPass = imagePassExecutor.ensureSwapchainClearRenderPass(swapchain.imageFormat)
        val clearRgba = environment.clearColorOr(clearColor)
        statsCounter.recordPass()
        VulkanCommands.vkCmdBeginRenderPassColor(
            commandBuffer = commandBuffer,
            renderPass = clearPass,
            framebuffer = framebuffer,
            width = swapchain.extent.width,
            height = swapchain.extent.height,
            r = clearRgba.r,
            g = clearRgba.g,
            b = clearRgba.b,
            a = clearRgba.a,
        )
        if (imagePassExecutor.drawsOnlyOnActiveTarget(chain)) {
            imagePassExecutor.recordInOpenPass(commandBuffer, frameIndex, parentTarget, chain, clearPass, statsCounter)
            Vulkan.vkCmdEndRenderPass(commandBuffer)
            return
        }
    }
    imagePassExecutor.executeImagePass(
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        parentTarget = parentTarget,
        chain = chain,
        hasMoreImagePasses = false,
        stats = statsCounter,
        parentPassOpenOnEntry = clearDestination,
        leaveParentPassOpen = false,
    )
}

private fun resolveSceneDraws(passInput: GpuPassInput?, drawCalls: List<PreparedDraw>) =
    passInput?.drawsForRecording() ?: sortForRecording(drawCalls)

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
            parentPassOpenOnEntry = false,
            leaveParentPassOpen = false,
        )
    }
}
