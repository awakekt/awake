/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.enums.VkPipelineBindPoint
import com.awakekt.awake.vulkan.enums.VkPresentModeKHR
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.models.VkExtensionProperties
import com.awakekt.awake.vulkan.models.VkLayerProperties
import com.awakekt.awake.vulkan.models.VkQueueFamilyProperties
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkSurfaceCapabilitiesKHR
import com.awakekt.awake.vulkan.models.VkSurfaceFormatKHR
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkAndroidSurfaceCreateInfoKHR
import com.awakekt.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkCommandBufferBeginInfo
import com.awakekt.awake.vulkan.models.info.VkCommandPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDeviceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkFenceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkInstanceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkPresentInfoKHR
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSemaphoreCreateInfo
import com.awakekt.awake.vulkan.models.info.VkShaderModuleCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSubmitInfo
import com.awakekt.awake.vulkan.models.info.VkSwapchainCreateInfoKHR
import com.awakekt.awake.vulkan.models.info.debug.VkDebugUtilsMessengerCreateInfoEXT
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineCacheCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineLayoutCreateInfo
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceFeatures
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceProperties

/**
 * Android actual of [Vulkan]. Every function is a JNI `external` declaration resolved against the
 * `awake-vulkan` native library, which this object loads when it is first used. A failed Vulkan
 * call throws a [com.awakekt.awake.vulkan.utils.VkResultException].
 */
actual object Vulkan {
    init {
        System.loadLibrary("awake-vulkan")
    }

    /** Creates a new Vulkan instance with the provided application information. */
    actual external fun vkCreateInstance(createInfo: VkInstanceCreateInfo): Long

    /** Destroys the specified Vulkan instance. */
    actual external fun vkDestroyInstance(instance: Long)

    /**
     * Enumerates the Vulkan extension properties available for the instance.
     *
     * @return An array of VkExtensionProperties representing the available instance extensions.
     */
    actual external fun vkEnumerateInstanceLayerProperties(): Array<VkLayerProperties>

    /** Enumerates the Vulkan extension properties available for the instance. */
    actual external fun vkEnumerateInstanceExtensionProperties(layerName: String?): Array<VkExtensionProperties>

    /** Enumerates the Vulkan extension properties available for a specific physical device. */
    actual external fun vkEnumerateDeviceExtensionProperties(
        physicalDevice: Long,
        layerName: String?,
    ): Array<VkExtensionProperties>

    /** Enumerates the available Vulkan physical devices for the specified instance. */
    actual external fun vkEnumeratePhysicalDevices(instance: Long): LongArray

    /** Retrieves properties of the specified physical device. */
    actual external fun vkGetPhysicalDeviceProperties(physicalDevice: Long): VkPhysicalDeviceProperties

    /** Retrieves features of the specified physical device. */
    actual external fun vkGetPhysicalDeviceFeatures(physicalDevice: Long): VkPhysicalDeviceFeatures

    /** Retrieves properties of the queue families available on the specified physical device. */
    actual external fun vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice: Long): Array<VkQueueFamilyProperties>

    /** Retrieves the images associated with the specified Vulkan swapchain. */
    actual external fun vkGetSwapchainImagesKHR(device: Long, swapchain: Long): LongArray

    /**
     * Creates a new VkDevice object associated with the given physical device and using the
     * provided device configuration.
     */
    actual external fun vkCreateDevice(physicalDevice: Long, deviceInfo: VkDeviceCreateInfo): Long

    /** Destroys the specified VkDevice object and releases its associated resources. */
    actual external fun vkDestroyDevice(device: Long)

    /**
     * Returns the VkQueue associated with the given device, queue family index, and queue index.
     */
    actual external fun vkGetDeviceQueue(device: Long, queueFamilyIndex: Int, queueIndex: Int): Long

    /** Creates an Android surface for Vulkan presentation. */
    actual external fun vkCreateAndroidSurfaceKHR(
        instance: Long,
        surfaceInfo: VkAndroidSurfaceCreateInfoKHR,
    ): Long

    /** Checks if presentation is supported on the specified physical device and queue family. */
    actual external fun vkGetPhysicalDeviceSurfaceSupportKHR(
        physicalDevice: Long,
        queueFamilyIndex: Int,
        surface: Long,
    ): Boolean

    /** Destroys the Vulkan surface. */
    actual external fun vkDestroySurfaceKHR(instance: Long, surface: Long)

    /** Retrieves the capabilities of the surface on the specified physical device. */
    actual external fun vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
        physicalDevice: Long,
        surface: Long,
    ): VkSurfaceCapabilitiesKHR

    /** Retrieves the available surface formats on the specified physical device. */
    actual external fun vkGetPhysicalDeviceSurfaceFormatsKHR(
        physicalDevice: Long,
        surface: Long,
    ): Array<VkSurfaceFormatKHR>

    /**
     * Retrieves the supported presentation modes for the specified surface on the physical device.
     */
    actual external fun vkGetPhysicalDeviceSurfacePresentModesKHR(
        physicalDevice: Long,
        surface: Long,
    ): Array<VkPresentModeKHR>

    /** Creates a Vulkan swapchain for the specified device. */
    actual external fun vkCreateSwapchainKHR(
        device: Long,
        createInfoKHR: VkSwapchainCreateInfoKHR,
    ): Long

    /** Destroys the Vulkan swapchain. */
    actual external fun vkDestroySwapchainKHR(
        device: Long,
        swapchainKHR: Long,
    )

    /**
     * Creates a debug messenger that forwards validation and driver messages to the callback in
     * [createInfo]. It needs `VK_EXT_debug_utils` enabled on the instance.
     */
    actual external fun vkCreateDebugUtilsMessengerEXT(
        instance: Long,
        createInfo: VkDebugUtilsMessengerCreateInfoEXT,
    ): Long

    /** Destroys a debug messenger. The callback is not invoked afterwards. */
    actual external fun vkDestroyDebugUtilsMessengerEXT(instance: Long, debugUtilsMessenger: Long)

    /**
     * Creates a view of an image, which fixes how a shader or framebuffer reads its format,
     * dimensionality and subresource range.
     */
    actual external fun vkCreateImageView(
        device: Long,
        createInfo: VkImageViewCreateInfo,
    ): Long

    /** Destroys an image view. The view must no longer be used by pending GPU work. */
    actual external fun vkDestroyImageView(device: Long, imageView: Long)

    /** Wraps SPIR-V code in a shader module that pipelines can name as a stage. */
    actual external fun vkCreateShaderModule(
        device: Long,
        createInfo: VkShaderModuleCreateInfo,
    ): Long

    /**
     * Destroys a shader module. It may be destroyed as soon as the pipelines that use it have been
     * created.
     */
    actual external fun vkDestroyShaderModule(device: Long, shaderModule: Long)

    /** Creates a pipeline cache that speeds up building pipelines with similar state. */
    actual external fun vkCreatePipelineCache(
        device: Long,
        createInfo: VkPipelineCacheCreateInfo,
    ): Long

    /** Destroys a pipeline cache. Pipelines created through it stay valid. */
    actual external fun vkDestroyPipelineCache(
        device: Long,
        pipelineCache: Long,
    )

    /** Builds one graphics pipeline for each entry of [createInfos]. */
    actual external fun vkCreateGraphicsPipelines(
        device: Long,
        pipelineCache: Long,
        createInfos: Array<VkGraphicsPipelineCreateInfo>,
    ): LongArray

    /**
     * Destroys a pipeline. Command buffers that recorded a bind of it must have finished executing.
     */
    actual external fun vkDestroyPipeline(device: Long, pipeline: Long)

    /**
     * Creates a pipeline layout, which lists the descriptor set layouts and push-constant ranges a
     * pipeline's shaders may access.
     */
    actual external fun vkCreatePipelineLayout(
        device: Long,
        createInfo: VkPipelineLayoutCreateInfo,
    ): Long

    /** Destroys a pipeline layout. Submitted commands that use it must have completed. */
    actual external fun vkDestroyPipelineLayout(device: Long, pipelineLayout: Long)

    /**
     * Creates a render pass describing the attachments, subpasses and dependencies of a rendering
     * sequence.
     */
    actual external fun vkCreateRenderPass(
        device: Long,
        createInfo: VkRenderPassCreateInfo,
    ): Long

    /** Destroys a render pass. Submitted commands that use it must have completed. */
    actual external fun vkDestroyRenderPass(device: Long, renderPass: Long)

    /** Creates a framebuffer that binds image views to a render pass's attachments. */
    actual external fun vkCreateFramebuffer(
        device: Long,
        framebufferInfo: VkFramebufferCreateInfo,
    ): Long

    /** Destroys a framebuffer. It must no longer be used by pending GPU work. */
    actual external fun vkDestroyFramebuffer(device: Long, framebuffer: Long)

    /**
     * Allocates one command buffer from the pool named in [createInfo].
     *
     * Despite the plural name this binding returns a single handle, so `commandBufferCount` must be
     * 1. The buffer is freed together with its pool by [vkDestroyCommandPool].
     */
    actual external fun vkAllocateCommandBuffers(
        device: Long,
        createInfo: VkCommandBufferAllocateInfo,
    ): Long

    /**
     * Starts recording into a command buffer, which must be in the initial state or have been
     * reset.
     */
    actual external fun vkBeginCommandBuffer(
        commandBuffer: Long,
        beginInfo: VkCommandBufferBeginInfo,
    )

    /** Creates a command pool from which command buffers for one queue family are allocated. */
    actual external fun vkCreateCommandPool(
        device: Long,
        createInfo: VkCommandPoolCreateInfo,
    ): Long

    /** Destroys a command pool together with every command buffer allocated from it. */
    actual external fun vkDestroyCommandPool(device: Long, commandPool: Long)

    /** Records binding of a pipeline for subsequent draw calls. */
    actual external fun vkCmdBindPipeline(
        commandBuffer: Long,
        pipelineBindPoint: VkPipelineBindPoint,
        graphicsPipeline: Long,
    )

    /** Records new viewport rectangles for a pipeline that declares the viewport dynamic. */
    actual external fun vkCmdSetViewport(
        commandBuffer: Long,
        firstViewport: Int,
        viewports: Array<VkViewport>,
    )

    /** Records new scissor rectangles for a pipeline that declares the scissor dynamic. */
    actual external fun vkCmdSetScissor(
        commandBuffer: Long,
        firstScissor: Int,
        scissors: Array<VkRect2D>,
    )

    /** Records a non-indexed draw. */
    actual external fun vkCmdDraw(
        commandBuffer: Long,
        vertexCount: Int,
        instanceCount: Int,
        firstVertex: Int,
        firstInstance: Int,
    )

    /** Records the start of a render pass on a framebuffer. */
    actual external fun vkCmdBeginRenderPass(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        renderPassBeginInfo: VkRenderPassBeginInfo,
        contents: VkSubpassContents,
    )

    /** Records the end of the current render pass. */
    actual external fun vkCmdEndRenderPass(commandBuffer: Long)

    /** Finishes recording, making the command buffer executable. */
    actual external fun vkEndCommandBuffer(commandBuffer: Long)

    /**
     * Creates a binary semaphore, used to order work between queues or between the GPU and
     * presentation.
     */
    @VkReturnType("VkSemaphore")
    actual external fun vkCreateSemaphore(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkSemaphoreCreateInfo,
    ): Long

    /** Destroys a semaphore. No pending work may still wait on or signal it. */
    actual external fun vkDestroySemaphore(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkSemaphore") semaphore: Long,
    )

    /**
     * Creates a fence, which the GPU signals when submitted work completes and the CPU can wait on.
     */
    @VkReturnType("VkFence")
    actual external fun vkCreateFence(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkFenceCreateInfo,
    ): Long

    /** Destroys a fence. It must not be part of a pending submission. */
    actual external fun vkDestroyFence(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkFence") fence: Long,
    )

    /**
     * Blocks the calling thread until the fences are signalled or the timeout elapses.
     *
     * Nothing is returned or thrown for a timeout or a lost device, so a finite timeout cannot be
     * told apart from a signalled fence. Pass [Long.MAX_VALUE] to wait effectively forever.
     */
    actual external fun vkWaitForFences(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkFence") fences: LongArray,
        waitAll: Boolean,
        timeout: Long,
    )

    /** Resets fences to the unsignalled state. None may be part of a pending submission. */
    actual external fun vkResetFences(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkFence") fences: LongArray,
    )

    /** The acquired image's index. A suboptimal swapchain still returns one; only errors throw. */
    actual external fun vkAcquireNextImageKHR(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkSwapchainKHR") swapchain: Long,
        timeout: Long,
        @VkHandleRef("VkSemaphore") semaphore: Long,
        @VkHandleRef("VkFence") fence: Long,
    ): Int

    /** Returns a command buffer to the initial state so it can be recorded again. */
    actual external fun vkResetCommandBuffer(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        flags: Int,
    )

    /** Submits recorded command buffers to a queue for execution. */
    actual external fun vkQueueSubmit(
        @VkHandleRef("VkQueue") queue: Long,
        pSubmits: Array<VkSubmitInfo>,
        @VkHandleRef("VkFence") fence: Long,
    )

    /**
     * Queues swapchain images for presentation after their wait semaphores signal.
     *
     * Anything other than `VK_SUCCESS` throws, including `VK_SUBOPTIMAL_KHR` and
     * `VK_ERROR_OUT_OF_DATE_KHR`, so a caller that wants to rebuild its swapchain on those catches
     * the exception and reads its `result`.
     */
    actual external fun vkQueuePresentKHR(
        @VkHandleRef("VkQueue") queue: Long,
        pPresentInfoKHR: VkPresentInfoKHR,
    )
}
