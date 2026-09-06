/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.enums.VkImageViewType
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo

/**
 * An offscreen color+depth render destination (`Renderer.createRenderTarget`) -- a
 * [com.awakekt.awake.render.texture.RenderTarget] implementation. The color
 * image is created with [colorFormat] equal to the swapchain's own image format (NOT
 * [Texture]'s fixed `R8G8B8A8_UNORM`) specifically so [renderPass] -- the SAME
 * `renderPipeline.renderPass`/pipeline the main swapchain pass already uses -- can be reused
 * unmodified: Vulkan requires a framebuffer's attachment image views to have the EXACT
 * format the render pass they're used with declares (not just a "compatible" one), so a
 * different color format here would force building an entire second render pass + graphics
 * pipeline (a real, but avoidable, amount of extra work for this feature). Whatever color
 * space interpretation the swapchain format implies (e.g. sRGB) applies identically when
 * this image is later sampled by a compositing [com.awakekt.awake.vulkan.material.Material]
 * -- no different code path is needed either way, a descriptor binding doesn't care what
 * format the bound image was created with.
 *
 * Resting layout is `SHADER_READ_ONLY_OPTIMAL` (set once, right after the render pass ends,
 * by [transitionToShaderReadOnly]) -- both compositing (sampling) and readback (a temporary
 * detour through `TRANSFER_SRC_OPTIMAL` and back, see `Renderer.readPixels`) start from
 * there.
 */
class OffscreenRenderTarget(
    graphicsDevice: GraphicsDevice,
    private val renderPass: Long,
    override val width: Int,
    override val height: Int,
    colorFormat: Int,
    private val onDestroy: (() -> Unit)? = null,
) : RenderTarget {
    private val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice

    var colorImage: Long = 0
        private set
    var colorImageMemory: Long = 0
        private set
    var colorImageView: Long = 0
        private set
    var sampler: Long = 0
        private set

    private var depthImage: Long = 0
    private var depthImageMemory: Long = 0
    private var depthImageView: Long = 0

    var framebuffer: Long = 0
        private set

    private var shaderReadable = false

    init {
        colorImage = VulkanImages.vkCreateImage(
            device,
            VkImageCreateInfo(
                width = width,
                height = height,
                format = colorFormat,
                usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or
                    VkImageUsageFlagBits2.VK_IMAGE_USAGE_SAMPLED_BIT or
                    VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
            ),
        )
        val colorRequirements = VulkanImages.vkGetImageMemoryRequirements(device, colorImage)
        val colorMemoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            colorRequirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
        )
        colorImageMemory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = colorRequirements.size, memoryTypeIndex = colorMemoryTypeIndex),
        )
        VulkanImages.vkBindImageMemory(device, colorImage, colorImageMemory, 0)
        colorImageView = Vulkan.vkCreateImageView(
            device,
            VkImageViewCreateInfo(
                image = colorImage,
                viewType = VkImageViewType.VK_IMAGE_VIEW_TYPE_2D,
                format = VkFormat.entries.first { it.value == colorFormat },
                subresourceRange = VkImageSubresourceRange(
                    aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_COLOR_BIT.value,
                    baseMipLevel = 0,
                    levelCount = 1,
                    baseArrayLayer = 0,
                    layerCount = 1,
                ),
            ),
        )
        sampler = VulkanImages.vkCreateSampler(device, VkSamplerCreateInfo())

        depthImage = VulkanImages.vkCreateImage(
            device,
            VkImageCreateInfo(
                width = width,
                height = height,
                format = VkFormat.VK_FORMAT_D32_SFLOAT.value,
                usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
            ),
        )
        val depthRequirements = VulkanImages.vkGetImageMemoryRequirements(device, depthImage)
        val depthMemoryTypeIndex = VulkanBuffers.findMemoryType(
            physicalDevice,
            depthRequirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
        )
        depthImageMemory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = depthRequirements.size, memoryTypeIndex = depthMemoryTypeIndex),
        )
        VulkanImages.vkBindImageMemory(device, depthImage, depthImageMemory, 0)
        depthImageView = Vulkan.vkCreateImageView(
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

        framebuffer = Vulkan.vkCreateFramebuffer(
            device,
            VkFramebufferCreateInfo(
                renderPass = renderPass,
                pAttachments = arrayOf(colorImageView, depthImageView),
                width = width,
                height = height,
                layers = 1,
            ),
        )
    }

    /** Called once, right after the render pass that wrote [colorImage] ends -- transitions
     * `COLOR_ATTACHMENT_OPTIMAL` -> `SHADER_READ_ONLY_OPTIMAL`, this target's resting layout
     * (see this class's doc comment). Must run inside the same one-time command buffer
     * `Renderer.renderToTexture` already opened for the render pass itself. */
    fun transitionToShaderReadOnly(commandBuffer: Long) {
        if (shaderReadable) return
        VulkanImages.vkTransitionImageLayout(
            commandBuffer,
            colorImage,
            VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
        )
        shaderReadable = true
    }

    /** Returns a target that was sampled in an earlier pass to a writable attachment layout. */
    fun prepareForColorAttachment(commandBuffer: Long) {
        if (!shaderReadable) return
        VulkanImages.vkTransitionImageLayout(
            commandBuffer,
            colorImage,
            VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VkImageLayout2.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
        )
        shaderReadable = false
    }

    /**
     * Idempotent, because this target has two legitimate owners.
     *
     * `Renderer.createRenderTarget` tracks every live target and frees anything still tracked in
     * `Renderer.destroy`, while a caller holding the target may also call this -- `FrameCapture`
     * does, and so does any test that tears its target down before the renderer. Neither is wrong;
     * without this guard whichever ran second double-freed a live VkFramebuffer, which the validation layer reports as
     * `vkDestroyFramebuffer(): Invalid VkFramebuffer Object` and only shows up where validation
     * is on.
     */
    private var destroyed = false

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        Vulkan.vkDestroyFramebuffer(device, framebuffer)
        Vulkan.vkDestroyImageView(device, depthImageView)
        VulkanImages.vkDestroyImage(device, depthImage)
        VulkanBuffers.vkFreeMemory(device, depthImageMemory)
        VulkanImages.vkDestroySampler(device, sampler)
        Vulkan.vkDestroyImageView(device, colorImageView)
        VulkanImages.vkDestroyImage(device, colorImage)
        VulkanBuffers.vkFreeMemory(device, colorImageMemory)
        onDestroy?.invoke()
    }
}
