/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.enums.flags.VkCommandBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkCommandBufferBeginInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo

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
    recordDepthPrePass(commandBuffer, drawCalls, cascades, true)
    // The camera's own depth, expressed as the one "cascade" this frame renders from the eye.
    recordSceneDepthPass(commandBuffer, drawCalls, cameraDepthPass(viewProjection))
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = framebuffers[acquiredImageIndex],
            renderArea = VkRect2D(extent = swapchainManager.extent),
            pClearValues = arrayOf(clearColorValue, Renderer.clearDepthValue),
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
        environmentState = environment,
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

    val uiPipeline = uiRenderPipeline
    if (uiPipeline != null) {
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = uiPipeline.renderPass,
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
) {
    Vulkan.vkBeginCommandBuffer(
        commandBuffer,
        VkCommandBufferBeginInfo(
            flags = VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
        ),
    )
    recordDepthPrePass(commandBuffer, depthDraws, prePasses, environment.shadowsEnabled)
    recordSceneDepthPass(commandBuffer, depthDraws, cameraDepthPass(viewProjection))
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = framebuffers[acquiredImageIndex],
            renderArea = VkRect2D(extent = swapchainManager.extent),
            pClearValues = arrayOf(clearColorValue, Renderer.clearDepthValue),
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
        environmentState = environment,
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

    val uiPipeline = uiRenderPipeline
    if (uiPipeline != null) {
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            VkRenderPassBeginInfo(
                renderPass = uiPipeline.renderPass,
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
    Vulkan.vkEndCommandBuffer(commandBuffer)
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
