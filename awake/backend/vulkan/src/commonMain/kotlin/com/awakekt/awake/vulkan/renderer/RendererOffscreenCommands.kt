/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkCommandBufferLevel
import com.awakekt.awake.vulkan.enums.flags.VkCommandBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkCommandBufferBeginInfo
import com.awakekt.awake.vulkan.models.info.VkFenceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSubmitInfo

/** Reuses one transfer-pool command buffer and fence for synchronous offscreen work. */
internal fun Renderer.runOffscreenCommands(block: (Long) -> Unit) {
    if (offscreenCommandBuffer == 0L) {
        offscreenCommandBuffer = Vulkan.vkAllocateCommandBuffers(
            device,
            VkCommandBufferAllocateInfo(
                commandPool = transferContext.commandPool.handle,
                level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
                commandBufferCount = 1,
            ),
        )
        offscreenFence = Vulkan.vkCreateFence(device, VkFenceCreateInfo())
    }
    Vulkan.vkResetCommandBuffer(offscreenCommandBuffer, 0)
    Vulkan.vkBeginCommandBuffer(
        offscreenCommandBuffer,
        VkCommandBufferBeginInfo(
            flags = VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
        ),
    )
    block(offscreenCommandBuffer)
    Vulkan.vkEndCommandBuffer(offscreenCommandBuffer)

    Vulkan.vkResetFences(device, longArrayOf(offscreenFence))
    Vulkan.vkQueueSubmit(
        graphicsQueue,
        arrayOf(VkSubmitInfo(pCommandBuffers = arrayOf(offscreenCommandBuffer))),
        offscreenFence,
    )
    Vulkan.vkWaitForFences(device, longArrayOf(offscreenFence), true, Long.MAX_VALUE)
}
