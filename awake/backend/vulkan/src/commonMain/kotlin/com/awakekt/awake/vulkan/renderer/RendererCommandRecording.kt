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
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = framebuffers[acquiredImageIndex],
            renderArea = VkRect2D(extent = swapchainManager.extent),
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
    val sceneRect = sceneViewport?.clampedTo(
        swapchainManager.extent.width.toFloat(),
        swapchainManager.extent.height.toFloat(),
    )
    Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(sceneRect?.toVkViewport() ?: viewport))
    Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(sceneRect?.toVkScissor() ?: scissor))
    bindDepthSets(commandBuffer)
    recordSharedPassFeatures(RenderPassSlot.Scene, context)
    Vulkan.vkCmdEndRenderPass(commandBuffer)

    recordUiOrPresentTransitionPass(
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
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
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = framebuffers[acquiredImageIndex],
            renderArea = VkRect2D(extent = swapchainManager.extent),
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
        passInput = passInput,
        acquiredImageIndex = acquiredImageIndex,
    )
    val viewport = VkViewport(
        width = swapchainManager.extent.width.toFloat(),
        height = swapchainManager.extent.height.toFloat(),
    )
    val scissor = VkRect2D(extent = swapchainManager.extent)
    val sceneRect = sceneViewport?.clampedTo(
        swapchainManager.extent.width.toFloat(),
        swapchainManager.extent.height.toFloat(),
    )
    Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(sceneRect?.toVkViewport() ?: viewport))
    Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(sceneRect?.toVkScissor() ?: scissor))
    bindDepthSets(commandBuffer)
    recordSharedPassFeatures(RenderPassSlot.Scene, context)
    Vulkan.vkCmdEndRenderPass(commandBuffer)

    recordPostPasses(commandBuffer, postPasses)
    recordSwapchainPostPasses(
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        acquiredImageIndex = acquiredImageIndex,
        chain = passInput?.postPassChain ?: ImagePassChain.Empty,
    )

    recordUiOrPresentTransitionPass(
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        acquiredImageIndex = acquiredImageIndex,
        viewport = viewport,
        scissor = scissor,
        context = context,
    )
    gpuFrameTimer?.end(commandBuffer, frameIndex)
    Vulkan.vkEndCommandBuffer(commandBuffer)
}

@Suppress("LongParameterList")
private fun Renderer.recordUiOrPresentTransitionPass(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    viewport: VkViewport,
    scissor: VkRect2D,
    context: VulkanFrameContext,
) {
    val uiPipeline = uiRenderPipeline
    if (uiPipeline != null) {
        imagePassExecutor.beginFrame(frameIndex)
        val hasImagePass = uiRuns.any { it is DrawRun.ImagePassRun }
        val initialRenderPass = if (hasImagePass) {
            imagePassExecutor.ensureSwapchainContinueRenderPass(swapchainManager.imageFormat)
        } else {
            uiPipeline.renderPass
        }
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

@Suppress("ReturnCount")
internal fun Renderer.recordSwapchainPostPasses(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    chain: ImagePassChain,
) {
    if (chain.isEmpty) return
    val swapchain = swapchainManager
    val colorImage = swapchain.images.getOrNull(acquiredImageIndex)
    val colorImageView = swapchain.imageViews.getOrNull(acquiredImageIndex)
    val framebuffer = uiFramebuffers.getOrNull(acquiredImageIndex)
    if (colorImage == null || colorImageView == null || framebuffer == null) return
    imagePassExecutor.beginFrame(frameIndex)
    val continuePass = imagePassExecutor.ensureSwapchainContinueRenderPass(swapchain.imageFormat)
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = continuePass,
            framebuffer = framebuffer,
            renderArea = VkRect2D(extent = swapchain.extent),
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
) {
    val chain = input.postPassChain
    if (!chain.isEmpty) {
        val swapchain = swapchainManager
        imagePassExecutor.beginFrame(commandBuffers.size)
        val continuePass = imagePassExecutor.ensureOffscreenContinueRenderPass(swapchain.imageFormat)
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = continuePass,
                framebuffer = target.framebuffer,
                renderArea = VkRect2D(extent = VkExtent2D(target.width, target.height)),
            ),
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        val parentTarget = VulkanImagePassParentTarget(
            framebuffer = target.framebuffer,
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
            frameIndex = commandBuffers.size,
            parentTarget = parentTarget,
            chain = chain,
            hasMoreImagePasses = false,
            stats = statsCounter,
        )
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    }
}
