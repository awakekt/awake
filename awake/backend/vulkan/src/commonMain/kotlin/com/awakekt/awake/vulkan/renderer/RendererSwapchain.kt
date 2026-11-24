/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.allocateTransientAttachmentMemory
import com.awakekt.awake.vulkan.enums.VkCommandBufferLevel
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.pipeline.createScenePresentRenderPass
import com.awakekt.awake.vulkan.texture.ColorAttachment

/** Swapchain-sized resource lifecycle -- depth buffer, framebuffers, direct-present scene pass,
 * lazy swapchain colour framebuffers, per-frame command buffers, and [recreateSwapChain]'s full
 * teardown/rebuild after `VK_SUBOPTIMAL_KHR`/`VK_ERROR_OUT_OF_DATE_KHR`. See [Renderer]'s class
 * doc comment for why this lives here as `internal` extension functions rather than as members. */

internal fun Renderer.createDepthResources() {
    val imageCount = swapchainManager.imageViews.size.coerceAtLeast(1)
    val images = mutableListOf<Long>()
    val memories = mutableListOf<Long>()
    val imageViews = mutableListOf<Long>()
    repeat(imageCount) {
        val depthImage = VulkanImages.vkCreateImage(
            device,
            VkImageCreateInfo(
                width = swapchainManager.extent.width,
                height = swapchainManager.extent.height,
                format = Renderer.DEPTH_FORMAT,
                usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT or
                    VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT,
                samples = renderPipeline.multisample.count,
            ),
        )
        val requirements = VulkanImages.vkGetImageMemoryRequirements(device, depthImage)
        val depthImageMemory = graphicsDevice.allocateTransientAttachmentMemory(depthImage, requirements)
        VulkanImages.vkBindImageMemory(device, depthImage, depthImageMemory, 0)
        val depthImageView = Vulkan.vkCreateImageView(
            device,
            VkImageViewCreateInfo(
                image = depthImage,
                viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
                format = VkFormat.VK_FORMAT_D32_SFLOAT,
                subresourceRange = VkImageSubresourceRange(
                    aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value,
                    baseMipLevel = 0,
                    levelCount = 1,
                    baseArrayLayer = 0,
                    layerCount = 1,
                ),
            ),
        )
        images += depthImage
        memories += depthImageMemory
        imageViews += depthImageView
    }
    depthImages = images
    depthImageMemories = memories
    depthImageViews = imageViews
}

internal fun Renderer.createFramebuffers() {
    multisampleColors = if (renderPipeline.multisample.count > 1) {
        swapchainManager.imageViews.map {
            ColorAttachment(graphicsDevice, swapchainManager.extent.width, swapchainManager.extent.height, swapchainManager.imageFormat.value, renderPipeline.multisample.count)
        }
    } else {
        emptyList()
    }
    framebuffers = swapchainManager.imageViews.mapIndexed { index, imageView ->
        val frameBufferInfo = VkFramebufferCreateInfo(
            renderPass = renderPipeline.renderPass,
            // One depth image per swapchain framebuffer so multiple frames can be in
            // flight without racing depth writes through a single shared attachment.
            pAttachments = multisampleColors.getOrNull(index)?.let { arrayOf(it.view, depthImageViews[index], imageView) }
                ?: arrayOf(imageView, depthImageViews[index]),
            width = swapchainManager.extent.width,
            height = swapchainManager.extent.height,
            layers = 1,
        )
        Vulkan.vkCreateFramebuffer(device, frameBufferInfo)
    }.toList()
}

/**
 * Returns a scene render pass compatible with [Renderer.renderPipeline]'s pass that transitions the
 * swapchain colour attachment directly to `VK_IMAGE_LAYOUT_PRESENT_SRC_KHR` at pass end, avoiding
 * an empty present-transition render pass when no post-pass or UI runs.
 */
internal fun Renderer.ensureSceneToPresentRenderPass(): Long {
    val format = swapchainManager.imageFormat
    if (sceneToPresentRenderPass == 0L || sceneToPresentRenderPassFormat != format) {
        if (sceneToPresentRenderPass != 0L) {
            Vulkan.vkDestroyRenderPass(device, sceneToPresentRenderPass)
            sceneToPresentRenderPass = 0L
        }
        if (swapchainManager.imageViews.isNotEmpty()) {
            sceneToPresentRenderPass = createScenePresentRenderPass(
                graphicsDevice,
                swapchainManager,
                renderPipeline.multisample.count,
            )
            sceneToPresentRenderPassFormat = format
        }
    }
    return sceneToPresentRenderPass
}

/**
 * Lazily builds single-attachment swapchain colour framebuffers for swapchain post-passes /
 * upscaling when no UI pipeline has built [Renderer.uiFramebuffers] yet.
 */
internal fun Renderer.ensureSwapchainColorFramebuffers(): List<Long> {
    if (swapchainColorFramebuffers.size != swapchainManager.imageViews.size || swapchainColorFramebuffers.isEmpty()) {
        swapchainColorFramebuffers.forEach { Vulkan.vkDestroyFramebuffer(device, it) }
        swapchainColorFramebuffers = if (swapchainManager.imageViews.isEmpty()) {
            emptyList()
        } else {
            val renderPass = imagePassExecutor.ensureSwapchainContinueRenderPass(swapchainManager.imageFormat)
            swapchainManager.imageViews.map { imageView ->
                Vulkan.vkCreateFramebuffer(
                    device,
                    VkFramebufferCreateInfo(
                        renderPass = renderPass,
                        pAttachments = arrayOf(imageView),
                        width = swapchainManager.extent.width,
                        height = swapchainManager.extent.height,
                        layers = 1,
                    ),
                )
            }
        }
    }
    return swapchainColorFramebuffers
}

internal fun Renderer.createCommandBuffers(commandPool: Long, maxFramesInFlight: Int) {
    val allocInfo = VkCommandBufferAllocateInfo(
        commandPool = commandPool,
        level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        commandBufferCount = 1,
    )
    for (i in 0 until maxFramesInFlight) {
        commandBuffers[i] = Vulkan.vkAllocateCommandBuffers(device, allocInfo)
    }
}

internal fun Renderer.destroyDepthResources() {
    multisampleColors.forEach { it.destroy() }
    multisampleColors = emptyList()
    depthImageViews.forEach { Vulkan.vkDestroyImageView(device, it) }
    depthImages.forEach { VulkanImages.vkDestroyImage(device, it) }
    depthImageMemories.forEach { VulkanBuffers.vkFreeMemory(device, it) }
    depthImageViews = emptyList()
    depthImages = emptyList()
    depthImageMemories = emptyList()
}

/** Rebuilds the swapchain (and everything sized off it -- depth image, main + UI
 * framebuffers) after `vkAcquireNextImageKHR`/`vkQueuePresentKHR` reports
 * `VK_SUBOPTIMAL_KHR`/`VK_ERROR_OUT_OF_DATE_KHR` (typically a window resize/maximize).
 * Doesn't touch any graphics pipeline (main, UI-quad, glyph, or texture-quad): all of
 * them declare `VK_DYNAMIC_STATE_VIEWPORT`/`SCISSOR`, so `recordCommandBuffer` already
 * sets the correct viewport/scissor from the live (post-recreate) [Renderer.swapchainManager]
 * `.extent` every frame -- only the UI pipelines' screen-size uniform buffers (used for
 * their vertex shaders' pixel-to-NDC math, not dynamic state) need rewriting here. */
internal fun Renderer.recreateSwapChain() {
    // The rebuild's wait is not a presented interval.
    presentIntervals.reset()
    // GLFW reports a 0x0 framebuffer while minimized, and CAMetalLayer can do the same while
    // it has no drawable. Keep the still-valid old swapchain until a later frame has an extent
    // the driver can actually create.
    if (!swapchainManager.hasDrawableExtent()) return
    releaseSwapchainResources()
    rebuildSwapchainResources()
}

/**
 * Frees the swapchain and everything sized off it (depth image, main, present-transition and UI
 * framebuffers). The device, pipelines and every app resource stay valid, so a window surface can
 * be released and later replaced (an Android app going to the background) without tearing down
 * the renderer.
 */
internal fun Renderer.releaseSwapchainResources() {
    // Nothing may still be reading/writing swapchain-derived resources (framebuffers,
    // depth image, image views) while they're torn down.
    VulkanBuffers.vkDeviceWaitIdle(device)

    sceneScale.release()
    imagePassExecutor.releaseTransientTargets()
    imagePassExecutor.invalidateAllStepDescriptors()
    framebuffers.forEach { Vulkan.vkDestroyFramebuffer(device, it) }
    swapchainColorFramebuffers.forEach { Vulkan.vkDestroyFramebuffer(device, it) }
    uiFramebuffers.forEach { Vulkan.vkDestroyFramebuffer(device, it) }
    framebuffers = emptyList()
    swapchainColorFramebuffers = emptyList()
    uiFramebuffers = emptyList()
    destroyDepthResources()
    // Also clears the handle: swapchainManager.create() chains from it, and it must not
    // reference an already-destroyed swapchain.
    swapchainManager.destroy()
}

/** Builds the swapchain and its sized resources against the device's current surface. */
internal fun Renderer.rebuildSwapchainResources() {
    swapchainManager.create()
    createDepthResources()
    createFramebuffers()

    val uiPipeline = uiRenderPipeline
    if (uiPipeline != null) {
        uiFramebuffers = buildUiFramebuffers(uiPipeline.renderPass)
        uiPipeline.writeScreenSize(swapchainManager.extent.width.toFloat(), swapchainManager.extent.height.toFloat())
    }
    uiGlyphRenderPipeline?.writeScreenSize(
        swapchainManager.extent.width.toFloat(),
        swapchainManager.extent.height.toFloat(),
    )
    uiDecoratedGlyphRenderPipeline?.writeScreenSize(
        swapchainManager.extent.width.toFloat(),
        swapchainManager.extent.height.toFloat(),
    )
    uiTextureRenderPipelines.values.forEach {
        it.writeScreenSize(
            swapchainManager.extent.width.toFloat(),
            swapchainManager.extent.height.toFloat(),
        )
    }
    uiRoundedQuadRenderPipeline?.writeScreenSize(
        swapchainManager.extent.width.toFloat(),
        swapchainManager.extent.height.toFloat(),
    )
}
