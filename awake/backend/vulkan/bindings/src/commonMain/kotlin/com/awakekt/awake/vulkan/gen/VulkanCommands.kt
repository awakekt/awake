/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.enums.VkResult

/**
 * Primitive-argument, zero-allocation Vulkan command-recording, synchronization, and submission
 * entry points.
 *
 * Every function passes raw handles and scalar primitives directly across JNI / cinterop so hot
 * per-frame and per-draw paths avoid allocating Kotlin descriptor wrappers, arrays, or running
 * per-call JNI field/class reflection.
 */
@Suppress("TooManyFunctions")
expect object VulkanCommands {
    /** Begins recording into [commandBuffer] with raw `VkCommandBufferUsageFlags` [flags]. */
    fun vkBeginCommandBuffer(commandBuffer: Long, flags: Int)

    /** Binds graphics [pipeline] (`VK_PIPELINE_BIND_POINT_GRAPHICS`) on [commandBuffer]. */
    fun vkCmdBindGraphicsPipeline(commandBuffer: Long, pipeline: Long)

    /** Sets viewport 0 on [commandBuffer] from scalar coordinates. */
    fun vkCmdSetViewportRect(
        commandBuffer: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        minDepth: Float,
        maxDepth: Float,
    )

    /** Sets scissor 0 on [commandBuffer] from scalar integer coordinates. */
    fun vkCmdSetScissorRect(commandBuffer: Long, x: Int, y: Int, width: Int, height: Int)

    /** Begins an inline render pass at origin `(0, 0)` with size `[width] x [height]` and no clear values. */
    fun vkCmdBeginRenderPassEmpty(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
    )

    /** Begins an inline render pass at origin `(0, 0)` clearing attachment 0 to RGBA `(r, g, b, a)`. */
    @Suppress("LongParameterList")
    fun vkCmdBeginRenderPassColor(
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
    fun vkCmdBeginRenderPassDepth(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
        depth: Float,
    )

    /**
     * Begins an inline render pass at origin `(0, 0)` clearing attachment 0 to RGBA `(r, g, b, a)`
     * and attachment 1 to depth [depth] and stencil 0.
     */
    @Suppress("LongParameterList")
    fun vkCmdBeginRenderPassColorDepth(
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
    fun vkWaitForFence(device: Long, fence: Long, timeout: Long)

    /** Resets a single [fence] on [device]. */
    fun vkResetFence(device: Long, fence: Long)

    /**
     * Submits a single [commandBuffer] to [queue], optionally waiting on [waitSemaphore] at
     * [waitStageMask], signalling [signalSemaphore], and signalling [fence]. Pass `0L` for any
     * unused semaphore or fence handle.
     */
    @Suppress("LongParameterList")
    fun vkQueueSubmitSingle(
        queue: Long,
        commandBuffer: Long,
        waitSemaphore: Long,
        waitStageMask: Int,
        signalSemaphore: Long,
        fence: Long,
    )

    /**
     * Presents [imageIndex] of [swapchain] on [queue], optionally waiting on [waitSemaphore]
     * (`0L` for none), routing through any active present hook (such as Swappy on Android).
     *
     * Answers `VK_SUCCESS`, `VK_SUBOPTIMAL_KHR`, and `VK_ERROR_OUT_OF_DATE_KHR` without throwing;
     * any other failure throws.
     */
    fun vkQueuePresentSingleStatus(
        queue: Long,
        swapchain: Long,
        imageIndex: Int,
        waitSemaphore: Long,
    ): VkResult

    /**
     * Creates a `VkPipelineCache` optionally seeded with [initialData] bytes previously exported
     * by [vkGetPipelineCacheData]. Drivers ignore incompatible or corrupt cache blobs safely.
     */
    fun vkCreatePipelineCacheFromBytes(device: Long, initialData: ByteArray?): Long

    /**
     * Exports the current contents of [pipelineCache] as raw bytes suitable for disk persistence.
     */
    fun vkGetPipelineCacheData(device: Long, pipelineCache: Long): ByteArray

    /**
     * Queries supported Vulkan 1.2/1.3 core features on [physicalDevice] using
     * `vkGetPhysicalDeviceFeatures2` (loaded via `vkGetInstanceProcAddr` so Vulkan 1.0 loaders
     * stay link-safe). Returns a bitmask of [VulkanModernFeatures] constants.
     */
    fun vkQueryModernFeatures(instance: Long, physicalDevice: Long, apiVersion: Int): Int

    /**
     * Creates a logical `VkDevice` enabling only the Vulkan 1.0 features in [features10Mask]
     * ([VulkanFeatures10]) and, on Vulkan 1.2/1.3 devices, chaining native
     * `VkPhysicalDeviceFeatures2` -> `VkPhysicalDeviceVulkan12Features` /
     * `VkPhysicalDeviceVulkan13Features` for [modernFeaturesMask] ([VulkanModernFeatures]).
     */
    @Suppress("LongParameterList")
    fun vkCreateLogicalDeviceWithFeatures(
        physicalDevice: Long,
        queueFamilyIndices: IntArray,
        enabledExtensions: Array<String>,
        features10Mask: Int,
        apiVersion: Int,
        modernFeaturesMask: Int,
    ): Long
}

/**
 * Bitmask constants for the selective Vulkan 1.0 features Awake enables on a logical device.
 *
 * Keeps `robustBufferAccess` and other unused 1.0 features disabled.
 */
object VulkanFeatures10 {
    /** Enables `VkPhysicalDeviceFeatures.fullDrawIndexUint32`. */
    const val FULL_DRAW_INDEX_UINT32: Int = 1 shl 0

    /** Enables `VkPhysicalDeviceFeatures.multiDrawIndirect`. */
    const val MULTI_DRAW_INDIRECT: Int = 1 shl 1

    /** Enables `VkPhysicalDeviceFeatures.depthClamp`. */
    const val DEPTH_CLAMP: Int = 1 shl 2

    /** Enables `VkPhysicalDeviceFeatures.fillModeNonSolid`. */
    const val FILL_MODE_NON_SOLID: Int = 1 shl 3

    /** Enables `VkPhysicalDeviceFeatures.samplerAnisotropy`. */
    const val SAMPLER_ANISOTROPY: Int = 1 shl 4

    /** Enables `VkPhysicalDeviceFeatures.textureCompressionETC2`. */
    const val TEXTURE_COMPRESSION_ETC2: Int = 1 shl 5

    /** Enables `VkPhysicalDeviceFeatures.textureCompressionASTC_LDR`. */
    const val TEXTURE_COMPRESSION_ASTC_LDR: Int = 1 shl 6

    /** Enables `VkPhysicalDeviceFeatures.textureCompressionBC`. */
    const val TEXTURE_COMPRESSION_BC: Int = 1 shl 7
}

/**
 * Bitmask constants for Vulkan 1.2 and 1.3 core features queried via `vkGetPhysicalDeviceFeatures2`
 * and genuinely enabled via `VkDeviceCreateInfo.pNext` chaining.
 */
object VulkanModernFeatures {
    /** Vulkan 1.2 `timelineSemaphore`. */
    const val TIMELINE_SEMAPHORE: Int = 1 shl 0

    /**
     * Vulkan 1.2 bindless descriptor indexing bundle (`descriptorIndexing`,
     * `shaderSampledImageArrayNonUniformIndexing`, `descriptorBindingSampledImageUpdateAfterBind`,
     * `descriptorBindingPartiallyBound`, `descriptorBindingVariableDescriptorCount`,
     * `runtimeDescriptorArray`).
     */
    const val DESCRIPTOR_INDEXING: Int = 1 shl 1

    /** Vulkan 1.2 `bufferDeviceAddress`. */
    const val BUFFER_DEVICE_ADDRESS: Int = 1 shl 2

    /** Vulkan 1.2 `drawIndirectCount`. */
    const val DRAW_INDIRECT_COUNT: Int = 1 shl 3

    /** Vulkan 1.2 `scalarBlockLayout`. */
    const val SCALAR_BLOCK_LAYOUT: Int = 1 shl 4

    /** Vulkan 1.3 `synchronization2`. */
    const val SYNCHRONIZATION_2: Int = 1 shl 5

    /** Vulkan 1.3 `dynamicRendering`. */
    const val DYNAMIC_RENDERING: Int = 1 shl 6

    /** Vulkan 1.3 `maintenance4`. */
    const val MAINTENANCE_4: Int = 1 shl 7

    /** Vulkan 1.3 `pipelineCreationCacheControl`. */
    const val PIPELINE_CREATION_CACHE_CONTROL: Int = 1 shl 8
}

internal fun vkResultFromPresentCode(code: Int): VkResult = when (code) {
    VkResult.VK_SUCCESS.value -> VkResult.VK_SUCCESS
    VkResult.VK_SUBOPTIMAL_KHR.value -> VkResult.VK_SUBOPTIMAL_KHR
    VkResult.VK_ERROR_OUT_OF_DATE_KHR.value -> VkResult.VK_ERROR_OUT_OF_DATE_KHR
    else -> VkResult.entries.first { it.value == code }
}
