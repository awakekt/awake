/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.host.FrameCostReport
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanCommands
import kotlin.time.TimeSource

/** Submits the recorded frame and presents it, including swapchain recovery. */
internal fun Renderer.submitAndPresent(currentFrame: Int, imageIndex: Int) {
    // Vulkan keeps no render bundles, so it creates no argument buffers.
    statsCounter.bufferCreationTotals(
        argumentBuffers = 0L,
        instanceBuffers = graphicsDevice.instanceBuffersCreated,
        uniformBuffers = graphicsDevice.uniformBuffersCreated,
    )
    statsCounter.publish(gpuFrameTimer?.lastMs)
    reportFrameCost()
    frameWaits.publish()
    // Headless: nothing signalled an image-available semaphore, because nothing acquired an
    // image -- so waiting on one would hang here rather than fail. Submit bare and return; the
    // fence this frame already owns is what a reader waits on.
    if (swapchainManager.isHeadlessPresentable) {
        graphicsDevice.queueLock.withLock {
            VulkanCommands.vkQueueSubmitSingle(
                queue = graphicsQueue,
                commandBuffer = commandBuffers[currentFrame],
                waitSemaphore = 0L,
                waitStageMask = 0,
                signalSemaphore = 0L,
                fence = swapchainManager.inFlightFences[currentFrame],
            )
        }
        bufferPools.frameSubmitted(currentFrame)
        retiredSamplers.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)
        deferredResourceReaper.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)
        transferContext.uploadBatcher.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)
        swapchainManager.currentFrame = (currentFrame + 1) % commandBuffers.size
        notifyFirstFrameSubmitted()
        return
    }
    val waitSemaphore = swapchainManager.imageAvailableSemaphores[currentFrame]
    val signalSemaphore = swapchainManager.renderFinishedSemaphores[imageIndex]

    graphicsDevice.queueLock.withLock {
        VulkanCommands.vkQueueSubmitSingle(
            queue = graphicsQueue,
            commandBuffer = commandBuffers[currentFrame],
            waitSemaphore = waitSemaphore,
            waitStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT.value,
            signalSemaphore = signalSemaphore,
            fence = swapchainManager.inFlightFences[currentFrame],
        )
    }
    bufferPools.frameSubmitted(currentFrame)
    retiredSamplers.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)
    deferredResourceReaper.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)
    transferContext.uploadBatcher.frameSubmitted(commandBuffers.size, ::awaitSubmittedOffscreenCommands)

    // Suboptimal and out of date come back as results, not exceptions: a phone panel whose
    // swapchain is not pre-rotated presents suboptimally every frame. The surface is asked once per
    // swapchain whether it changed; a resize or rotation the platform reports re-asks (#1688).
    // A change of pacer or of frame-rate mode applies here, between frames, on the thread that presents.
    graphicsDevice.swapchainPacer?.beforePresent()
    val result = present(swapchainManager.swapChain, imageIndex, signalSemaphore)
    swapchainManager.presentResults[result.ordinal]++
    presentIntervals.recordPresent()
    when (presentAction(result, swapchainManager.suboptimalAccepted) { swapchainManager.surfaceChangedSinceBuild }) {
        PresentAction.Recreate -> recreateSwapChain()
        PresentAction.AcceptSuboptimal -> swapchainManager.suboptimalAccepted = true
        PresentAction.None -> Unit
    }
    swapchainManager.currentFrame = (currentFrame + 1) % commandBuffers.size
    notifyFirstFrameSubmitted()
}

private fun Renderer.notifyFirstFrameSubmitted() {
    val hook = onFirstFrameSubmitted ?: return
    onFirstFrameSubmitted = null
    hook()
}

/**
 * Tells the frame loop what this frame waited on and its GPU time (#1699): it counts a frame's own
 * work, not its waits, when it picks a steady rate. Before [FrameWaits.publish] clears the sums.
 */
private fun Renderer.reportFrameCost() {
    val ms = gpuFrameTimer?.lastMsOrNaN ?: Float.NaN
    val gpuNanos = if (ms.isFinite() && ms > 0f) (ms * NANOS_PER_MS).toLong() else 0L
    FrameCostReport.submitted(frameWaits.fenceNanos + frameWaits.acquireNanos, gpuNanos)
}

/** Presents, counting the call as a wait: a pacer holds the frame here, as Swappy does (#1699). */
private fun Renderer.present(swapchain: Long, imageIndex: Int, waitSemaphore: Long): VkResult {
    val start = TimeSource.Monotonic.markNow()
    val result = graphicsDevice.queueLock.withLock {
        VulkanCommands.vkQueuePresentSingleStatus(presentQueue, swapchain, imageIndex, waitSemaphore)
    }
    FrameCostReport.submitted(start.elapsedNow().inWholeNanoseconds, 0L)
    return result
}

private const val NANOS_PER_MS = 1_000_000f
