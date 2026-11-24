/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.enums.VkResult

/** Desktop JVM actual of [VulkanCommands], backed by direct primitive JNI entry points. */
@Suppress("TooManyFunctions")
actual object VulkanCommands {
    /** Begins recording into [commandBuffer] with raw `VkCommandBufferUsageFlags` [flags]. */
    actual external fun vkBeginCommandBuffer(commandBuffer: Long, flags: Int)

    /** Binds graphics [pipeline] (`VK_PIPELINE_BIND_POINT_GRAPHICS`) on [commandBuffer]. */
    actual external fun vkCmdBindGraphicsPipeline(commandBuffer: Long, pipeline: Long)

    /** Sets viewport 0 on [commandBuffer] from scalar coordinates. */
    actual external fun vkCmdSetViewportRect(
        commandBuffer: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        minDepth: Float,
        maxDepth: Float,
    )

    /** Sets scissor 0 on [commandBuffer] from scalar integer coordinates. */
    actual external fun vkCmdSetScissorRect(commandBuffer: Long, x: Int, y: Int, width: Int, height: Int)

    /** Begins an inline render pass at origin `(0, 0)` with size `[width] x [height]` and no clear values. */
    actual external fun vkCmdBeginRenderPassEmpty(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
    )

    /** Begins an inline render pass at origin `(0, 0)` clearing attachment 0 to RGBA `(r, g, b, a)`. */
    @Suppress("LongParameterList")
    actual external fun vkCmdBeginRenderPassColor(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    )

    /** Begins an inline render pass at origin `(0, 0)` clearing attachment 0 to depth [depth] and stencil 0. */
    @Suppress("LongParameterList")
    actual external fun vkCmdBeginRenderPassDepth(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
        depth: Float,
    )

    /** Begins an inline render pass clearing attachment 0 to RGBA `(r, g, b, a)` and attachment 1 to depth [depth]. */
    @Suppress("LongParameterList")
    actual external fun vkCmdBeginRenderPassColorDepth(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        depth: Float,
    )

    /** Waits for a single [fence] on [device] up to [timeout] nanoseconds. */
    actual external fun vkWaitForFence(device: Long, fence: Long, timeout: Long)

    /** Resets a single [fence] on [device]. */
    actual external fun vkResetFence(device: Long, fence: Long)

    /** Submits a single [commandBuffer] to [queue] with optional wait/signal semaphores and [fence]. */
    @Suppress("LongParameterList")
    actual external fun vkQueueSubmitSingle(
        queue: Long,
        commandBuffer: Long,
        waitSemaphore: Long,
        waitStageMask: Int,
        signalSemaphore: Long,
        fence: Long,
    )

    /** Presents [imageIndex] of [swapchain] on [queue], answering `VK_SUBOPTIMAL_KHR` and `VK_ERROR_OUT_OF_DATE_KHR`. */
    actual fun vkQueuePresentSingleStatus(
        queue: Long,
        swapchain: Long,
        imageIndex: Int,
        waitSemaphore: Long,
    ): VkResult = vkResultFromPresentCode(
        nativeQueuePresentSingleStatus(queue, swapchain, imageIndex, waitSemaphore),
    )

    private external fun nativeQueuePresentSingleStatus(
        queue: Long,
        swapchain: Long,
        imageIndex: Int,
        waitSemaphore: Long,
    ): Int

    /** Creates a `VkPipelineCache` optionally seeded with [initialData] bytes. */
    actual external fun vkCreatePipelineCacheFromBytes(device: Long, initialData: ByteArray?): Long

    /** Exports the current contents of [pipelineCache] as raw bytes. */
    actual external fun vkGetPipelineCacheData(device: Long, pipelineCache: Long): ByteArray

    /** Queries supported Vulkan 1.2/1.3 core features via `vkGetPhysicalDeviceFeatures2`. */
    actual external fun vkQueryModernFeatures(instance: Long, physicalDevice: Long, apiVersion: Int): Int

    /** Creates a logical `VkDevice` with selective 1.0 and chained 1.2/1.3 feature structs. */
    @Suppress("LongParameterList")
    actual external fun vkCreateLogicalDeviceWithFeatures(
        physicalDevice: Long,
        queueFamilyIndices: IntArray,
        enabledExtensions: Array<String>,
        features10Mask: Int,
        apiVersion: Int,
        modernFeaturesMask: Int,
    ): Long
}
