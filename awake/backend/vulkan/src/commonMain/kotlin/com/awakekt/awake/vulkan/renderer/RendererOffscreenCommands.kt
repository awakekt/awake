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
    // The submitted work shares the offscreen frame's slots with this.
    awaitSubmittedOffscreenCommands()
    if (offscreenCommandBuffer == 0L) {
        offscreenCommandBuffer = allocateOffscreenCommandBuffer()
        offscreenFence = Vulkan.vkCreateFence(device, VkFenceCreateInfo())
    }
    recordAndSubmit(offscreenCommandBuffer, offscreenFence, block)
    Vulkan.vkWaitForFences(device, longArrayOf(offscreenFence), true, Long.MAX_VALUE)
}

/**
 * [runOffscreenCommands] without the wait: the work is submitted and this returns, so the CPU goes
 * on with its frame while the GPU renders. Work submitted later on the queue still runs after it.
 *
 * At most one such submission is outstanding. The next one, and any synchronous offscreen work,
 * waits for it first: they share the offscreen frame's command buffer slots and cascade matrices.
 */
internal fun Renderer.submitOffscreenCommands(block: (Long) -> Unit) {
    awaitSubmittedOffscreenCommands()
    if (submittedOffscreenCommandBuffer == 0L) {
        submittedOffscreenCommandBuffer = allocateOffscreenCommandBuffer()
        submittedOffscreenFence = Vulkan.vkCreateFence(device, VkFenceCreateInfo())
    }
    recordAndSubmit(submittedOffscreenCommandBuffer, submittedOffscreenFence, block)
    submittedOffscreenFrame = swapchainManager.currentFrame
}

/** Waits for the outstanding [submitOffscreenCommands] work, if any. */
internal fun Renderer.awaitSubmittedOffscreenCommands() {
    if (submittedOffscreenFrame < 0) return
    Vulkan.vkWaitForFences(device, longArrayOf(submittedOffscreenFence), true, Long.MAX_VALUE)
    submittedOffscreenFrame = NO_SUBMITTED_OFFSCREEN_FRAME
}

/**
 * Waits for outstanding offscreen work that wrote [frameIndex]'s uniform and instance slots, before
 * those slots are rewritten. Work from another frame slot is left running.
 */
internal fun Renderer.awaitSubmittedOffscreenCommandsFor(frameIndex: Int) {
    if (submittedOffscreenFrame == frameIndex) awaitSubmittedOffscreenCommands()
}

internal const val NO_SUBMITTED_OFFSCREEN_FRAME = -1

private fun Renderer.allocateOffscreenCommandBuffer(): Long = Vulkan.vkAllocateCommandBuffers(
    device,
    VkCommandBufferAllocateInfo(
        commandPool = transferContext.commandPool.handle,
        level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        commandBufferCount = 1,
    ),
)

private fun Renderer.recordAndSubmit(commandBuffer: Long, fence: Long, block: (Long) -> Unit) {
    Vulkan.vkResetCommandBuffer(commandBuffer, 0)
    Vulkan.vkBeginCommandBuffer(
        commandBuffer,
        VkCommandBufferBeginInfo(
            flags = VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
        ),
    )
    block(commandBuffer)
    Vulkan.vkEndCommandBuffer(commandBuffer)

    Vulkan.vkResetFences(device, longArrayOf(fence))
    Vulkan.vkQueueSubmit(
        graphicsQueue,
        arrayOf(VkSubmitInfo(pCommandBuffers = arrayOf(commandBuffer))),
        fence,
    )
}
