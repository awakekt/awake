/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import platform.MoltenVK.VK_IMAGE_ASPECT_COLOR_BIT
import platform.MoltenVK.VkBufferImageCopy
import platform.MoltenVK.vkCmdCopyBufferToImage

@OptIn(ExperimentalForeignApi::class)
@Suppress("LongParameterList") // Primitive arguments mirror the native region-copy boundary.
actual object VulkanTextureRegions {
    actual fun copy(commandBuffer: Long, buffer: Long, image: Long, layer: Int, x: Int, y: Int, width: Int, height: Int) = memScoped {
        val region = alloc<VkBufferImageCopy>().apply {
            bufferOffset = 0uL
            bufferRowLength = 0u
            bufferImageHeight = 0u
            imageSubresource.apply {
                aspectMask = VK_IMAGE_ASPECT_COLOR_BIT.toUInt()
                mipLevel = 0u
                baseArrayLayer = layer.toUInt()
                layerCount = 1u
            }
            imageOffset.apply {
                this.x = x
                this.y = y
                z = 0
            }
            imageExtent.apply {
                this.width = width.toUInt()
                this.height = height.toUInt()
                depth = 1u
            }
        }
        vkCmdCopyBufferToImage(commandBuffer.toCPointer(), buffer.toCPointer(), image.toCPointer(), VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL.toUInt(), 1u, region.ptr)
    }
}
