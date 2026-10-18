/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.graphics2d.BackdropBlurQuality
import com.awakekt.awake.render.passes2d.BackdropBlurTarget
import com.awakekt.awake.render.passes2d.UiPipelineKind
import com.awakekt.awake.render.pipeline.UiPipelineDescriptor
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.pipeline.VulkanBackdropTarget
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import com.awakekt.awake.vulkan.ui.BackdropBlurResources
import com.awakekt.awake.vulkan.ui.UiRenderPipeline

/**
 * Backdrop blur on Vulkan: the levels and pipelines, built the first time a frame blurs, and the
 * description of whichever target a UI pass is drawing into.
 */

/**
 * The swapchain UI pass's backdrop: the window at its current size, at the renderer's quality, or
 * Off when its images cannot be sampled.
 */
internal fun Renderer.swapchainBackdropTarget(): BackdropBlurTarget {
    val quality = if (swapchainManager.sampleable && swapchainManager.imageViews.isNotEmpty()) backdropBlurQuality else BackdropBlurQuality.Off
    return BackdropBlurTarget(quality, swapchainManager.extent.width, swapchainManager.extent.height)
}

/** Builds the blur resources once and grows their levels to hold a [width] by [height] backdrop. */
internal fun Renderer.ensureBackdropResources(width: Int, height: Int) {
    val resources = backdropResources ?: BackdropBlurResources(
        graphicsDevice,
        swapchainManager.imageFormat,
        requireNotNull(uiBackdropBlurShaders) { "This renderer was built without ui_backdrop_blur shader resources." },
        maxFramesInFlight,
    ).also { backdropResources = it }
    resources.ensureLevels(width, height)
}

/** The swapchain glass pipeline and the render pass that resumes the UI pass after a blur. */
internal fun Renderer.ensureSwapchainBackdropPipeline() {
    val uiPass = requireNotNull(uiRenderPipeline) { "ensureUiQuadPipeline() must run first." }.renderPass
    if (uiContinueRenderPass == 0L) {
        uiContinueRenderPass = BackdropBlurResources.createContinueRenderPass(device, swapchainManager.imageFormat, depthFormat = null)
    }
    if (uiBackdropCompositePipeline == null) uiBackdropCompositePipeline = buildBackdropCompositePipeline(uiPass)
}

/**
 * The swapchain image [imageIndex] as the UI pass's backdrop, or null when this frame blurs
 * nothing. The pass ends in the present layout, so the last blur resumes it with the UI pass
 * itself and every earlier one with the continuing pass.
 */
internal fun Renderer.swapchainBackdropPass(imageIndex: Int): VulkanBackdropTarget? {
    if (uiBlurCount == 0 || imageIndex !in swapchainManager.images.indices) return null
    val resources = backdropResources ?: return null
    val composite = uiBackdropCompositePipeline ?: return null
    val uiPass = uiRenderPipeline?.renderPass ?: return null
    val image = swapchainManager.images[imageIndex]
    return VulkanBackdropTarget(
        resources = resources,
        composite = composite,
        view = swapchainManager.imageViews[imageIndex],
        width = swapchainManager.extent.width,
        height = swapchainManager.extent.height,
        framebuffer = uiFramebuffers[imageIndex],
        continuePass = uiContinueRenderPass,
        finalPass = uiPass,
        toShaderRead = { commandBuffer ->
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image,
                VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            )
        },
        toAttachment = { commandBuffer ->
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            )
        },
    )
}

/** The render pass the swapchain UI pass begins with: resumable when this frame blurs. */
internal fun Renderer.uiPassBeginRenderPass(uiPass: Long): Long =
    if (uiBlurCount > 0 && uiContinueRenderPass != 0L && uiBackdropCompositePipeline != null) uiContinueRenderPass else uiPass

/**
 * [target] as the backdrop of an offscreen UI pass. Its pass leaves colour in the attachment
 * layout either way, so every blur resumes it with the same continuing pass.
 */
internal fun Renderer.offscreenBackdropTarget(target: OffscreenRenderTarget): VulkanBackdropTarget? {
    val resources = backdropResources ?: return null
    if (offscreenContinueRenderPass == 0L) {
        offscreenContinueRenderPass = BackdropBlurResources.createContinueRenderPass(
            device,
            swapchainManager.imageFormat,
            depthFormat = VkFormat.VK_FORMAT_D32_SFLOAT,
        )
    }
    val composite = offscreenBackdropCompositePipeline
        ?: buildBackdropCompositePipeline(renderPipeline.renderPass).also { offscreenBackdropCompositePipeline = it }
    return VulkanBackdropTarget(
        resources = resources,
        composite = composite,
        view = target.colorImageView,
        width = target.width,
        height = target.height,
        framebuffer = target.framebuffer,
        continuePass = offscreenContinueRenderPass,
        finalPass = offscreenContinueRenderPass,
        toShaderRead = target::transitionToShaderReadOnly,
        toAttachment = target::prepareForColorAttachment,
    )
}

private fun Renderer.buildBackdropCompositePipeline(renderPass: Long): UiRenderPipeline {
    val shaders = requireNotNull(uiBackdropCompositeShaders) { "This renderer was built without ui_backdrop_composite shader resources." }
    return UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = shaders.vertex,
        fragShaderCode = shaders.fragment,
        descriptor = UiPipelineDescriptor(
            variant = UiPipelineKind.BackdropComposite.pipelineVariant,
            vertexFormat = UiPipelineKind.BackdropComposite.vertexFormat,
        ),
        externalRenderPass = renderPass,
        framesInFlight = maxFramesInFlight,
    )
}

/** Releases everything [ensureBackdropResources] and the pipeline builders made. */
internal fun Renderer.destroyBackdropResources() {
    uiBackdropCompositePipeline?.destroy()
    uiBackdropCompositePipeline = null
    offscreenBackdropCompositePipeline?.destroy()
    offscreenBackdropCompositePipeline = null
    if (uiContinueRenderPass != 0L) Vulkan.vkDestroyRenderPass(device, uiContinueRenderPass)
    if (offscreenContinueRenderPass != 0L) Vulkan.vkDestroyRenderPass(device, offscreenContinueRenderPass)
    uiContinueRenderPass = 0
    offscreenContinueRenderPass = 0
    backdropResources?.destroy()
    backdropResources = null
}
