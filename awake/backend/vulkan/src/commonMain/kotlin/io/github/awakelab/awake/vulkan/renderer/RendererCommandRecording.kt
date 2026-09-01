/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.renderer

import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.command.sortForRecording
import io.github.awakelab.awake.render.passes.RenderPassSlot
import io.github.awakelab.awake.render.renderer.ShadowCascadeUniforms
import io.github.awakelab.awake.render.renderer.shadowCascades
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.enums.VkSubpassContents
import io.github.awakelab.awake.vulkan.enums.flags.VkCommandBufferUsageFlagBits
import io.github.awakelab.awake.vulkan.models.VkRect2D
import io.github.awakelab.awake.vulkan.models.VkViewport
import io.github.awakelab.awake.vulkan.models.info.VkCommandBufferBeginInfo
import io.github.awakelab.awake.vulkan.models.info.VkRenderPassBeginInfo

/** Records the Vulkan swapchain scene pass and its UI or present-transition pass. */
internal fun Renderer.recordCommandBuffer(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    drawCalls: List<PreparedDrawCall>,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    light: SceneLight,
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
    recordDepthPrePass(commandBuffer, drawCalls, light.shadowCascades())
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
    val context = RendererFrameContext(
        renderer = this,
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        groupedDrawCalls = sorted.opaqueByPipeline,
        transparentDrawCalls = sorted.transparent,
        primaryPipeline = pipelineFor(renderPipeline.vertexFormat) ?: renderPipeline,
        viewProjection = viewProjection,
        cameraEye = cameraEye,
        light = light,
    )
    val viewport = VkViewport(
        width = swapchainManager.extent.width.toFloat(),
        height = swapchainManager.extent.height.toFloat(),
    )
    val scissor = VkRect2D(extent = swapchainManager.extent)
    val sceneRect = resolvedSceneViewport()
    Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(sceneRect?.toVkViewport() ?: viewport))
    Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(sceneRect?.toVkScissor() ?: scissor))
    bindDepthSet(commandBuffer)
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

/** The camera's view-projection as a one-entry cascade set -- what the scene-depth pass renders
 * from, through the same machinery the shadow pass uses. */
internal fun cameraDepthPass(viewProjection: Mat4): ShadowCascadeUniforms =
    ShadowCascadeUniforms(listOf(viewProjection), floatArrayOf(Float.MAX_VALUE))
