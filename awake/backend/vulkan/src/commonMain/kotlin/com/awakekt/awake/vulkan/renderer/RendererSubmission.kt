/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.models.info.VkPresentInfoKHR
import com.awakekt.awake.vulkan.models.info.VkSubmitInfo
import com.awakekt.awake.vulkan.utils.VkResultException

/**
 * Blocks until the GPU has finished every frame this renderer has submitted, swapchain and offscreen
 * alike. Meshes and materials reach it through [GraphicsDevice.awaitSubmittedFrames] before they free
 * their buffers. Narrower than `vkDeviceWaitIdle`, which also waits on queue work that is not a frame.
 */
internal fun Renderer.awaitSubmittedFrames() {
    awaitSubmittedOffscreenCommands()
    val fences = swapchainManager.inFlightFences.filter { it != 0L }.toLongArray()
    if (fences.isNotEmpty()) Vulkan.vkWaitForFences(device, fences, true, Long.MAX_VALUE)
}

/** Submits the recorded frame and presents it, including swapchain recovery. */
internal fun Renderer.submitAndPresent(currentFrame: Int, imageIndex: Int) {
    statsCounter.publish(gpuFrameTimer?.lastMs)
    // Headless: nothing signalled an image-available semaphore, because nothing acquired an
    // image -- so waiting on one would hang here rather than fail. Submit bare and return; the
    // fence this frame already owns is what a reader waits on.
    if (swapchainManager.isHeadlessPresentable) {
        Vulkan.vkQueueSubmit(
            graphicsQueue,
            arrayOf(VkSubmitInfo(pCommandBuffers = arrayOf(commandBuffers[currentFrame]))),
            swapchainManager.inFlightFences[currentFrame],
        )
        swapchainManager.currentFrame = (currentFrame + 1) % commandBuffers.size
        return
    }
    val waitSemaphores = arrayOf(swapchainManager.imageAvailableSemaphores[currentFrame])
    val waitStages =
        intArrayOf(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value)
    val signalSemaphores = arrayOf(swapchainManager.renderFinishedSemaphores[imageIndex])

    Vulkan.vkQueueSubmit(
        graphicsQueue,
        arrayOf(
            VkSubmitInfo(
                pWaitSemaphores = waitSemaphores,
                pWaitDstStageMask = waitStages,
                pCommandBuffers = arrayOf(commandBuffers[currentFrame]),
                pSignalSemaphores = signalSemaphores,
            ),
        ),
        swapchainManager.inFlightFences[currentFrame],
    )

    val presentInfo = VkPresentInfoKHR(
        pWaitSemaphores = signalSemaphores,
        pSwapchains = arrayOf(swapchainManager.swapChain),
        pImageIndices = intArrayOf(imageIndex),
        pResults = VkResult.values(),
    )
    try {
        Vulkan.vkQueuePresentKHR(presentQueue, presentInfo)
    } catch (e: VkResultException) {
        when (e.result) {
            VkResult.VK_SUBOPTIMAL_KHR -> if (swapchainManager.surfaceChangedSinceBuild) recreateSwapChain()
            VkResult.VK_ERROR_OUT_OF_DATE_KHR -> recreateSwapChain()
            else -> throw e
        }
    }
    swapchainManager.currentFrame = (currentFrame + 1) % commandBuffers.size
}
