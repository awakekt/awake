/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.vulkan.VK_SUBPASS_EXTERNAL
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkAttachmentLoadOp
import com.awakekt.awake.vulkan.enums.VkAttachmentStoreOp
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageLayout
import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.enums.flags.VkAccessFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.models.VkAttachmentDescription
import com.awakekt.awake.vulkan.models.VkAttachmentReference
import com.awakekt.awake.vulkan.models.VkSubpassDependency
import com.awakekt.awake.vulkan.models.info.VkRenderPassCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSubpassDescription
import com.awakekt.awake.vulkan.swapchain.SwapchainManager

/**
 * Render-pass-compatible variant of [createSceneRenderPass] whose final colour attachment (or
 * resolve attachment when multisampled) transitions directly to `VK_IMAGE_LAYOUT_PRESENT_SRC_KHR`.
 *
 * Compatible with every [RenderPipeline] and swapchain framebuffer built for [createSceneRenderPass]
 * (Vulkan §8.2 ignores `finalLayout` for compatibility), eliminating the empty present-transition
 * render pass on UI-less, unscaled frames.
 */
internal fun createScenePresentRenderPass(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    sampleCount: Int = 1,
): Long = createSceneRenderPassWithFinalLayout(
    graphicsDevice,
    swapchainManager,
    sampleCount,
    colorFinalLayout = VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
)

internal fun createSceneRenderPassWithFinalLayout(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    sampleCount: Int,
    colorFinalLayout: VkImageLayout,
): Long = Vulkan.vkCreateRenderPass(
    graphicsDevice.device,
    VkRenderPassCreateInfo(
        pAttachments = sceneAttachments(swapchainManager, sampleCount, colorFinalLayout),
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
                pResolveAttachments = if (sampleCount > 1) {
                    arrayOf(
                        VkAttachmentReference(attachment = 2, layout = VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL),
                    )
                } else {
                    null
                },
            ),
        ),
        pDependencies = sceneDependencies(),
    ),
)

/**
 * The scene pass's external dependencies (#1676). One depth attachment can be drawn by this pass from
 * two command buffers with nothing else ordering them -- an offscreen target two frames in flight
 * both render, say -- so the pass orders itself: in, its layout transition and clear wait for an
 * earlier pass's depth and colour writes at both fragment-test stages; out, its own colour and depth
 * writes are made available to the next pass (or to the final `PRESENT_SRC_KHR` transition). Both
 * `createSceneRenderPass` and `createScenePresentRenderPass` use this exact array so they remain
 * render-pass compatible (Vulkan §8.2 requires matching `pDependencies`).
 */
private fun sceneDependencies(): Array<VkSubpassDependency> {
    val attachmentStages = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value or
        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value or
        VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value
    val attachmentWrites = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT.value or
        VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value
    val depthAccess = VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT.value or
        VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value
    return arrayOf(
        VkSubpassDependency(
            srcSubpass = VK_SUBPASS_EXTERNAL,
            dstSubpass = 0,
            srcStageMask = attachmentStages,
            srcAccessMask = attachmentWrites,
            dstStageMask = attachmentStages,
            dstAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT.value or attachmentWrites or depthAccess,
        ),
        VkSubpassDependency(
            srcSubpass = 0,
            dstSubpass = VK_SUBPASS_EXTERNAL,
            srcStageMask = attachmentStages,
            srcAccessMask = attachmentWrites,
            dstStageMask = attachmentStages,
            dstAccessMask = VkAccessFlagBits.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT.value or attachmentWrites or depthAccess,
        ),
    )
}

private fun sceneAttachments(
    swapchainManager: SwapchainManager,
    sampleCount: Int,
    colorFinalLayout: VkImageLayout,
): Array<VkAttachmentDescription> =
    arrayOf(
        VkAttachmentDescription(
            format = swapchainManager.imageFormat,
            samples = sampleCount.toVkSampleCount(),
            storeOp = if (sampleCount > 1) VkAttachmentStoreOp.DONT_CARE else VkAttachmentStoreOp.STORE,
            initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
            finalLayout = if (sampleCount > 1) {
                VkImageLayout.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
            } else {
                colorFinalLayout
            },
        ),
        VkAttachmentDescription(
            format = VkFormat.VK_FORMAT_D32_SFLOAT,
            samples = sampleCount.toVkSampleCount(),
            storeOp = VkAttachmentStoreOp.DONT_CARE,
            initialLayout = VkImageLayout.VK_IMAGE_LAYOUT_UNDEFINED,
            finalLayout = VkImageLayout.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
        ),
    ) + if (sampleCount > 1) {
        arrayOf(
            VkAttachmentDescription(
                format = swapchainManager.imageFormat,
                loadOp = VkAttachmentLoadOp.DONT_CARE,
                finalLayout = colorFinalLayout,
            ),
        )
    } else {
        emptyArray()
    }
