/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2

/**
 * The attachments only a scene pass needs: depth and, above 1x, multisampled colour, both at the
 * plan's sample count, and the scene framebuffer that binds them around the resolved colour view.
 * An [OffscreenRenderTarget] builds them on its first scene pass, so a target that only UI passes
 * draw into, which bind the resolved 1x colour alone, never holds them.
 */
@Suppress("TooGenericExceptionCaught") // Every native-allocation failure must release earlier handles before propagating.
internal class SceneAttachments(
    private val graphicsDevice: GraphicsDevice,
    renderPass: Long,
    extent: VkExtent2D,
    colorFormat: Int,
    colorView: Long,
    sampleCount: Int,
) {
    /** The attachments this holder allocated, in framebuffer order before the resolved colour. */
    val attachments: List<ColorAttachment>

    /** Framebuffer for the scene render pass. */
    val framebuffer: Long

    init {
        val (width, height) = extent
        val allocated = mutableListOf<ColorAttachment>()
        try {
            val multisampleColor = if (sampleCount > 1) {
                ColorAttachment(graphicsDevice, width, height, colorFormat, sampleCount).also(allocated::add)
            } else {
                null
            }
            val depth = ColorAttachment(
                graphicsDevice,
                width,
                height,
                VkFormat.VK_FORMAT_D32_SFLOAT.value,
                sampleCount,
                usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value,
            ).also(allocated::add)
            framebuffer = Vulkan.vkCreateFramebuffer(
                graphicsDevice.device,
                VkFramebufferCreateInfo(
                    renderPass = renderPass,
                    pAttachments = multisampleColor?.let { arrayOf(it.view, depth.view, colorView) }
                        ?: arrayOf(colorView, depth.view),
                    width = width,
                    height = height,
                    layers = 1,
                ),
            )
        } catch (failure: Throwable) {
            allocated.forEach(ColorAttachment::destroy)
            throw failure
        }
        attachments = allocated
    }

    fun destroy() {
        Vulkan.vkDestroyFramebuffer(graphicsDevice.device, framebuffer)
        attachments.forEach(ColorAttachment::destroy)
    }
}

/** One image a render target holds: its Vulkan format, sample count and bound device memory. */
internal data class AttachmentAllocation(val format: Int, val samples: Int, val bytes: Long)

internal val ColorAttachment.allocation: AttachmentAllocation get() = AttachmentAllocation(format, samples, bytes)
