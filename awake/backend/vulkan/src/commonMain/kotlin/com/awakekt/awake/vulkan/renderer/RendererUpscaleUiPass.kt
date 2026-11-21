/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes2d.DrawRun
import com.awakekt.awake.render.renderer.GpuFramePart
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.pipeline.VulkanImagePassParentTarget

/**
 * A scaled frame's upscale and UI in one render pass on the swapchain image (#1709): it clears,
 * draws [upscale], then the UI, and ends ready to present. Recorded separately, they took three
 * native-size passes: a clear the image pass ended at once, the upscale on a reload, and the UI on
 * another reload. A tile-based GPU wrote the whole image three times and read it back twice.
 *
 * Returns false, recording nothing, when the frame needs the separate passes:
 * - no UI pipeline exists yet, so there is no UI framebuffer;
 * - the UI holds an image pass, which ends and begins the pass itself;
 * - [upscale] reads its destination or draws through a transient target.
 *
 * The frame timer's upscale mark is written inside the pass, between the two. On a tile-based GPU,
 * work inside one render pass may not finish in order, so the upscale and UI parts are best read
 * together there.
 */
@Suppress("LongParameterList")
internal fun Renderer.recordUpscaleAndUi(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    upscale: ImagePassChain,
    viewport: VkViewport,
    scissor: VkRect2D,
    context: VulkanFrameContext,
    environment: GpuEnvironmentState,
    timer: GpuFrameTimer?,
): Boolean {
    val swapchain = swapchainManager
    val framebuffer = uiFramebuffers.getOrNull(acquiredImageIndex)
    val colorImage = swapchain.images.getOrNull(acquiredImageIndex)
    val colorImageView = swapchain.imageViews.getOrNull(acquiredImageIndex)
    val merges = uiRenderPipeline != null && framebuffer != null && colorImage != null && colorImageView != null &&
        uiRuns.none { it is DrawRun.ImagePassRun } && imagePassExecutor.drawsOnlyOnActiveTarget(upscale)
    if (!merges) return false
    val renderPass = imagePassExecutor.ensureSwapchainPresentRenderPass(swapchain.imageFormat)
    statsCounter.recordPass()
    Vulkan.vkCmdBeginRenderPass(
        commandBuffer,
        VkRenderPassBeginInfo(
            renderPass = renderPass,
            framebuffer = framebuffer,
            renderArea = VkRect2D(extent = swapchain.extent),
            pClearValues = arrayOf(clearColorValue(environment)),
        ),
        VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
    )
    imagePassExecutor.recordInOpenPass(
        commandBuffer = commandBuffer,
        frameIndex = frameIndex,
        parentTarget = VulkanImagePassParentTarget(
            framebuffer = framebuffer,
            colorImage = colorImage,
            colorImageView = colorImageView,
            colorFormat = swapchain.imageFormat,
            width = swapchain.extent.width,
            height = swapchain.extent.height,
            isOffscreen = false,
            finalRenderPass = renderPass,
            sampleable = swapchain.sampleable,
        ),
        chain = upscale,
        renderPass = renderPass,
        stats = statsCounter,
    )
    timer?.mark(commandBuffer, frameIndex, GpuFramePart.Upscale)
    Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(viewport))
    Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(scissor))
    recordSharedPassFeatures(RenderPassSlot.Ui, context)
    Vulkan.vkCmdEndRenderPass(commandBuffer)
    return true
}
