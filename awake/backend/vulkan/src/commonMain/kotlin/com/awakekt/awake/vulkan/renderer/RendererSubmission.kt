/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.models.info.VkPresentInfoKHR
import com.awakekt.awake.vulkan.models.info.VkSubmitInfo

/** Submits the recorded frame and presents it, including swapchain recovery. */
internal fun Renderer.submitAndPresent(currentFrame: Int, imageIndex: Int) {
    // Vulkan keeps no render bundles, so it creates no argument buffers.
    statsCounter.bufferCreationTotals(
        argumentBuffers = 0L,
        instanceBuffers = graphicsDevice.instanceBuffersCreated,
        uniformBuffers = graphicsDevice.uniformBuffersCreated,
    )
    statsCounter.publish(gpuFrameTimer?.lastMs)
    frameWaits.publish()
    // Headless: nothing signalled an image-available semaphore, because nothing acquired an
    // image -- so waiting on one would hang here rather than fail. Submit bare and return; the
    // fence this frame already owns is what a reader waits on.
    if (swapchainManager.isHeadlessPresentable) {
        graphicsDevice.queueLock.withLock {
            Vulkan.vkQueueSubmit(
                graphicsQueue,
                arrayOf(VkSubmitInfo(pCommandBuffers = arrayOf(commandBuffers[currentFrame]))),
                swapchainManager.inFlightFences[currentFrame],
            )
        }
        bufferPools.frameSubmitted(currentFrame)
        retiredSamplers.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)
        swapchainManager.currentFrame = (currentFrame + 1) % commandBuffers.size
        return
    }
    val waitSemaphores = arrayOf(swapchainManager.imageAvailableSemaphores[currentFrame])
    val waitStages =
        intArrayOf(VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value)
    val signalSemaphores = arrayOf(swapchainManager.renderFinishedSemaphores[imageIndex])

    graphicsDevice.queueLock.withLock {
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
    }
    bufferPools.frameSubmitted(currentFrame)
    retiredSamplers.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)

    val presentInfo = VkPresentInfoKHR(
        pWaitSemaphores = signalSemaphores,
        pSwapchains = arrayOf(swapchainManager.swapChain),
        pImageIndices = intArrayOf(imageIndex),
        pResults = VkResult.values(),
    )
    // Suboptimal and out of date come back as results, not exceptions: a phone panel whose
    // swapchain is not pre-rotated presents suboptimally every frame. The surface is asked once per
    // swapchain whether it changed; a resize or rotation the platform reports re-asks (#1688).
    // A change of pacer or of frame-rate mode applies here, between frames, on the thread that presents.
    graphicsDevice.swapchainPacer?.beforePresent()
    val result = graphicsDevice.queueLock.withLock { Vulkan.vkQueuePresentKHRStatus(presentQueue, presentInfo) }
    swapchainManager.presentResults[result.ordinal]++
    presentIntervals.recordPresent()
    when (presentAction(result, swapchainManager.suboptimalAccepted) { swapchainManager.surfaceChangedSinceBuild }) {
        PresentAction.Recreate -> recreateSwapChain()
        PresentAction.AcceptSuboptimal -> swapchainManager.suboptimalAccepted = true
        PresentAction.None -> Unit
    }
    swapchainManager.currentFrame = (currentFrame + 1) % commandBuffers.size
}
