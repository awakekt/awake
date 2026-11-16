/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkCommandBufferLevel
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.models.VkClearColorValue
import com.awakekt.awake.vulkan.models.VkClearDepthStencilValue
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkCommandBufferBeginInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.models.info.VkSubmitInfo
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The scene render pass drawn twice into one offscreen target, from two command buffers submitted
 * back to back with no wait between them: what two frames in flight do to a target nothing else
 * orders (#1676). The second pass's layout transition of the depth attachment must come after the
 * first pass's depth writes, and only the pass's own subpass dependencies can say so.
 *
 * Run by `desktopSyncValidationTest`, with synchronization validation on; a `SYNC-HAZARD-*` is a
 * validation error, which fails the run when the headless device is destroyed. Negative control:
 * with the scene pass's dependencies as before (EARLY_FRAGMENT_TESTS and no access on the way in,
 * nothing on the way out), each pass after the first reports SYNC-HAZARD-WRITE-AFTER-WRITE on its
 * depth attachment.
 */
class ScenePassDepthHazardTest {
    @Test
    fun aTargetDrawnFromTwoCommandBuffersInARowHasNoHazard() {
        assertEquals("true", System.getenv("VK_KHRONOS_VALIDATION_VALIDATE_SYNC"), "run through desktopSyncValidationTest")
        val device = GraphicsDevice().apply { createHeadless() }
        try {
            val swapchain = SwapchainManager(device, 1).apply { createHeadless(SIZE, SIZE) }
            val renderPass = createSceneRenderPass(device, swapchain)
            val target = OffscreenRenderTarget(device, renderPass, SIZE, SIZE, swapchain.imageFormat.value)
            val transfer = TransferContext(device)
            try {
                val commandBuffers = List(FRAMES_IN_FLIGHT) {
                    Vulkan.vkAllocateCommandBuffers(
                        device.device,
                        VkCommandBufferAllocateInfo(
                            commandPool = transfer.commandPool.handle,
                            level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
                            commandBufferCount = 1,
                        ),
                    )
                }
                repeat(PASSES) { pass ->
                    val commandBuffer = commandBuffers[pass % FRAMES_IN_FLIGHT]
                    if (pass >= FRAMES_IN_FLIGHT) com.awakekt.awake.vulkan.gen.VulkanBuffers.vkDeviceWaitIdle(device.device)
                    Vulkan.vkResetCommandBuffer(commandBuffer, 0)
                    Vulkan.vkBeginCommandBuffer(commandBuffer, VkCommandBufferBeginInfo())
                    target.prepareForColorAttachment(commandBuffer)
                    Vulkan.vkCmdBeginRenderPass(
                        commandBuffer,
                        VkRenderPassBeginInfo(
                            renderPass = renderPass,
                            framebuffer = target.framebuffer,
                            renderArea = VkRect2D(extent = VkExtent2D(SIZE, SIZE)),
                            pClearValues = arrayOf(VkClearColorValue.Float32(), VkClearDepthStencilValue(depth = 1f, stencil = 0)),
                        ),
                        VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
                    )
                    Vulkan.vkCmdEndRenderPass(commandBuffer)
                    target.transitionToShaderReadOnly(commandBuffer)
                    Vulkan.vkEndCommandBuffer(commandBuffer)
                    Vulkan.vkQueueSubmit(device.graphicsQueue, arrayOf(VkSubmitInfo(pCommandBuffers = arrayOf(commandBuffer))), 0L)
                }
                com.awakekt.awake.vulkan.gen.VulkanBuffers.vkDeviceWaitIdle(device.device)
            } finally {
                target.destroy()
                transfer.destroy()
                Vulkan.vkDestroyRenderPass(device.device, renderPass)
                swapchain.destroy()
            }
        } finally {
            // createHeadless makes every validation error, a synchronization hazard among them, fail here.
            device.destroy()
        }
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
        const val PASSES = 6
    }
}
