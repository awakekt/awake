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
 * Desktop JVM actual of [Vulkan]. Every function is a JNI `external` declaration resolved against
 * the `awake-vulkan` native library, which [VulkanNativeLoader] loads, from `java.library.path` or
 * the copy embedded in the jar, when this object is first used. A failed Vulkan call throws a
 * [com.awakekt.awake.vulkan.utils.VkResultException]. [Vulkan.vkCreateAndroidSurfaceKHR] is not
 * implemented here and throws [NotImplementedError].
 */
actual object Vulkan {
    init {
        VulkanNativeLoader.load()
    }

    /**
     * Creates a new Vulkan instance with the provided application information.
     *
     * @param createInfo The VkInstanceCreateInfo containing the application-specific information.
     * @return The handle to the created Vulkan instance.
     */
    @VkReturnType("VkInstance")
    actual external fun vkCreateInstance(createInfo: VkInstanceCreateInfo): Long

    /**
     * Destroys the specified Vulkan instance.
     *
     * @param instance The handle to the Vulkan instance to be destroyed.
     */
    actual external fun vkDestroyInstance(@VkHandleRef("VkInstance") instance: Long)

    /**
     * Enumerates the Vulkan extension properties available for the instance.
     *
     * @return An array of VkExtensionProperties representing the available instance extensions.
     */
    actual external fun vkEnumerateInstanceLayerProperties(): Array<VkLayerProperties>

    /**
     * Enumerates the Vulkan extension properties available for the instance.
     *
     * @return An array of VkExtensionProperties representing the available instance extensions.
     */
    actual external fun vkEnumerateInstanceExtensionProperties(layerName: String?): Array<VkExtensionProperties>

    /**
     * Enumerates the Vulkan extension properties available for a specific physical device.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @param layerName Layer whose extensions to enumerate, or `null` for the implementation's own.
     * @return An array of [VkExtensionProperties] representing the available device extensions.
     */
    actual external fun vkEnumerateDeviceExtensionProperties(
        @VkHandleRef("VkPhysicalDevice") physicalDevice: Long,
        layerName: String?,
    ): Array<VkExtensionProperties>

    /**
     * Enumerates the available Vulkan physical devices for the specified instance.
     *
     * @param instance The handle to the Vulkan instance.
     * @return An array of VkPhysicalDevice handle in a form of type Long
     */
    @VkReturnType("VkPhysicalDevice")
    actual external fun vkEnumeratePhysicalDevices(@VkHandleRef("VkInstance") instance: Long): LongArray

    /**
     * Retrieves properties of the specified physical device.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @return The VkPhysicalDeviceProperties representing the properties of the physical device.
     */
    actual external fun vkGetPhysicalDeviceProperties(@VkHandleRef("VkPhysicalDevice") physicalDevice: Long): VkPhysicalDeviceProperties

    /**
     * Retrieves features of the specified physical device.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @return The VkPhysicalDeviceFeatures representing the features of the physical device.
     */
    actual external fun vkGetPhysicalDeviceFeatures(@VkHandleRef("VkPhysicalDevice") physicalDevice: Long): VkPhysicalDeviceFeatures

    /**
     * Retrieves properties of the queue families available on the specified physical device.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @return An array of VkQueueFamilyProperties representing the properties of queue families.
     */
    actual external fun vkGetPhysicalDeviceQueueFamilyProperties(@VkHandleRef("VkPhysicalDevice") physicalDevice: Long): Array<VkQueueFamilyProperties>

    /**
     * Retrieves the images associated with the specified Vulkan swapchain.
     *
     * @param device The handle to the Vulkan logical device.
     * @param swapchain The handle to the Vulkan swapchain.
     * @return An array of VkImage representing the images in the swapchain.
     */
    @VkReturnType("VkImage")
    actual external fun vkGetSwapchainImagesKHR(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkSwapchainKHR") swapchain: Long,
    ): LongArray

    /**
     * Creates a new VkDevice object associated with the given physical device and using the provided device configuration.
     *
     * @param physicalDevice The handle to the physical device for which the logical device will be created.
     * @param deviceInfo The configuration settings for the logical device, encapsulated in VkDeviceCreateInfo.
     * @return A handle to the newly created VkDevice object.
     */
    @VkReturnType("VkDevice")
    actual external fun vkCreateDevice(
        @VkHandleRef("VkPhysicalDevice") physicalDevice: Long,
        deviceInfo: VkDeviceCreateInfo,
    ): Long

    /**
     * Destroys the specified VkDevice object and releases its associated resources.
     *
     * @param device The handle to the logical device that will be destroyed.
     */
    actual external fun vkDestroyDevice(@VkHandleRef("VkDevice") device: Long)

    /**
     * Returns the VkQueue associated with the given device, queue family index, and queue index.
     *
     * @param device The handle to the logical device.
     * @param queueFamilyIndex The index of the queue family.
     * @param queueIndex The index of the queue within the queue family.
     * @return The VkQueue associated with the specified parameters.
     */
    @VkReturnType("VkQueue")
    actual external fun vkGetDeviceQueue(
        @VkHandleRef("VkDevice") device: Long,
        queueFamilyIndex: Int,
        queueIndex: Int,
    ): Long

    /**
     * Creates an Android surface for Vulkan presentation.
     *
     * @param instance The handle to the Vulkan instance.
     * @param surfaceInfo Information required to create the Android surface.
     * @return The handle to the created Android surface.
     */
    @VkReturnType("VkSurfaceKHR")
    actual fun vkCreateAndroidSurfaceKHR(
        @VkHandleRef("VkInstance") instance: Long,
        surfaceInfo: VkAndroidSurfaceCreateInfoKHR,
    ): Long {
        TODO("Not yet implemented")
    }

    /**
     * Checks if presentation is supported on the specified physical device and queue family.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @param queueFamilyIndex The index of the queue family to check for presentation support.
     * @param surface The handle to the surface to be presented.
     * @return `true` if presentation is supported, `false` otherwise.
     */
    actual external fun vkGetPhysicalDeviceSurfaceSupportKHR(
        @VkHandleRef("VkPhysicalDevice") physicalDevice: Long,
        queueFamilyIndex: Int,
        @VkHandleRef("VkSurfaceKHR") surface: Long,
    ): Boolean

    /**
     * Destroys the Vulkan surface.
     *
     * @param instance The handle to the Vulkan instance.
     * @param surface The handle to the surface to be destroyed.
     */
    actual external fun vkDestroySurfaceKHR(
        @VkHandleRef("VkInstance") instance: Long,
        @VkHandleRef("VkSurfaceKHR") surface: Long,
    )

    /**
     * Retrieves the capabilities of the surface on the specified physical device.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @param surface The handle to the surface to retrieve capabilities from.
     * @return The capabilities of the specified surface.
     */
    actual external fun vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
        @VkHandleRef("VkPhysicalDevice") physicalDevice: Long,
        @VkHandleRef("VkSurfaceKHR") surface: Long,
    ): VkSurfaceCapabilitiesKHR

    /**
     * Retrieves the available surface formats on the specified physical device.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @param surface The handle to the surface to query for formats.
     * @return An array of surface formats supported by the specified surface.
     */
    actual external fun vkGetPhysicalDeviceSurfaceFormatsKHR(
        @VkHandleRef("VkPhysicalDevice") physicalDevice: Long,
        @VkHandleRef("VkSurfaceKHR") surface: Long,
    ): Array<VkSurfaceFormatKHR>

    /**
     * Retrieves the supported presentation modes for the specified surface on the physical device.
     *
     * @param physicalDevice The handle to the Vulkan physical device.
     * @param surface The handle to the surface to query for presentation modes.
     * @return An array of supported presentation modes for the specified surface.
     */
    actual external fun vkGetPhysicalDeviceSurfacePresentModesKHR(
        @VkHandleRef("VkPhysicalDevice") physicalDevice: Long,
        @VkHandleRef("VkSurfaceKHR") surface: Long,
    ): Array<VkPresentModeKHR>

    /**
     * Creates a Vulkan swapchain for the specified device.
     *
     * @param device The handle to the Vulkan logical device.
     * @param createInfoKHR The structure containing swapchain creation information.
     * @return The handle to the created Vulkan swapchain.
     */
    @VkReturnType("VkSwapchainKHR")
    actual external fun vkCreateSwapchainKHR(
        @VkHandleRef("VkDevice") device: Long,
        createInfoKHR: VkSwapchainCreateInfoKHR,
    ): Long

    /**
     * Destroys the Vulkan swapchain.
     *
     * @param device The handle to the Vulkan logical device.
     * @param swapchainKHR The handle to the swapchain to be destroyed.
     */
    actual external fun vkDestroySwapchainKHR(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkSwapchainKHR") swapchainKHR: Long,
    )

    /**
     * Creates a view of an image, which fixes how a shader or framebuffer reads its format,
     * dimensionality and subresource range.
     */
    @VkReturnType("VkImageView")
    actual external fun vkCreateImageView(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkImageViewCreateInfo,
    ): Long

    /** Destroys an image view. The view must no longer be used by pending GPU work. */
    actual external fun vkDestroyImageView(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkImageView") imageView: Long,
    )

    /** Wraps SPIR-V code in a shader module that pipelines can name as a stage. */
    @VkReturnType("VkShaderModule")
    actual external fun vkCreateShaderModule(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkShaderModuleCreateInfo,
    ): Long

    /**
     * Destroys a shader module. It may be destroyed as soon as the pipelines that use it have been
     * created.
     */
    actual external fun vkDestroyShaderModule(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkShaderModule") shaderModule: Long,
    )

    /** Creates a pipeline cache that speeds up building pipelines with similar state. */
    @VkReturnType("VkPipelineCache")
    actual external fun vkCreatePipelineCache(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkPipelineCacheCreateInfo,
    ): Long

    /** Destroys a pipeline cache. Pipelines created through it stay valid. */
    actual external fun vkDestroyPipelineCache(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkPipelineCache") pipelineCache: Long,
    )

    /**
     * Creates a pipeline layout, which lists the descriptor set layouts and push-constant ranges a
     * pipeline's shaders may access.
     */
    @VkReturnType("VkPipelineLayout")
    actual external fun vkCreatePipelineLayout(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkPipelineLayoutCreateInfo,
    ): Long

    /** Destroys a pipeline layout. Submitted commands that use it must have completed. */
    actual external fun vkDestroyPipelineLayout(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkPipelineLayout") pipelineLayout: Long,
    )

    /** Builds one graphics pipeline for each entry of [createInfos]. */
    @VkReturnType("VkPipeline")
    actual external fun vkCreateGraphicsPipelines(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkPipelineCache") pipelineCache: Long,
        createInfos: Array<VkGraphicsPipelineCreateInfo>,
    ): LongArray

    /**
     * Destroys a pipeline. Command buffers that recorded a bind of it must have finished executing.
     */
    actual external fun vkDestroyPipeline(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkPipeline") pipeline: Long,
    )

    /**
     * Creates a render pass describing the attachments, subpasses and dependencies of a rendering
     * sequence.
     */
    @VkReturnType("VkRenderPass")
    actual external fun vkCreateRenderPass(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkRenderPassCreateInfo,
    ): Long

    /** Destroys a render pass. Submitted commands that use it must have completed. */
    actual external fun vkDestroyRenderPass(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkRenderPass") renderPass: Long,
    )

    /** Creates a framebuffer that binds image views to a render pass's attachments. */
    @VkReturnType("VkFramebuffer")
    actual external fun vkCreateFramebuffer(
        @VkHandleRef("VkDevice") device: Long,
        framebufferInfo: VkFramebufferCreateInfo,
    ): Long

    /** Destroys a framebuffer. It must no longer be used by pending GPU work. */
    actual external fun vkDestroyFramebuffer(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkFramebuffer") framebuffer: Long,
    )

    /**
     * Allocates one command buffer from the pool named in [createInfo].
     *
     * Despite the plural name this binding returns a single handle, so `commandBufferCount` must be
     * 1. The buffer is freed together with its pool by [vkDestroyCommandPool].
     */
    @VkReturnType("VkCommandBuffer")
    actual external fun vkAllocateCommandBuffers(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkCommandBufferAllocateInfo,
    ): Long

    /**
     * Starts recording into a command buffer, which must be in the initial state or have been
     * reset.
     */
    actual external fun vkBeginCommandBuffer(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        beginInfo: VkCommandBufferBeginInfo,
    )

    /** Creates a command pool from which command buffers for one queue family are allocated. */
    @VkReturnType("VkCommandPool")
    actual external fun vkCreateCommandPool(
        @VkHandleRef("VkDevice") device: Long,
        createInfo: VkCommandPoolCreateInfo,
    ): Long

    /** Destroys a command pool together with every command buffer allocated from it. */
    actual external fun vkDestroyCommandPool(
        @VkHandleRef("VkDevice") device: Long,
        @VkHandleRef("VkCommandPool") commandPool: Long,
    )

    /** Records binding of a pipeline for subsequent draw calls. */
    actual external fun vkCmdBindPipeline(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        pipelineBindPoint: VkPipelineBindPoint,
        @VkHandleRef("VkPipeline") graphicsPipeline: Long,
    )

    /** Records new viewport rectangles for a pipeline that declares the viewport dynamic. */
    actual external fun vkCmdSetViewport(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        firstViewport: Int,
        viewports: Array<VkViewport>,
    )

    /** Records new scissor rectangles for a pipeline that declares the scissor dynamic. */
    actual external fun vkCmdSetScissor(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        firstScissor: Int,
        scissors: Array<VkRect2D>,
    )

    /** Records a non-indexed draw. */
    actual external fun vkCmdDraw(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        vertexCount: Int,
        instanceCount: Int,
        firstVertex: Int,
        firstInstance: Int,
    )

    /** Records the end of the current render pass. */
    actual external fun vkCmdEndRenderPass(@VkHandleRef("VkCommandBuffer") commandBuffer: Long)

    /** Finishes recording, making the command buffer executable. */
    actual external fun vkEndCommandBuffer(@VkHandleRef("VkCommandBuffer") commandBuffer: Long)

    /**
     * Creates a debug messenger that forwards validation and driver messages to the callback in
     * [createInfo]. It needs `VK_EXT_debug_utils` enabled on the instance.
     */
    @VkReturnType("VkDebugUtilsMessengerEXT")
    @VkSingleton
    actual external fun vkCreateDebugUtilsMessengerEXT(
        @VkHandleRef("VkInstance") instance: Long,
        createInfo: VkDebugUtilsMessengerCreateInfoEXT,
    ): Long

    /** Destroys a debug messenger. The callback is not invoked afterwards. */
    @VkSingleton
    actual external fun vkDestroyDebugUtilsMessengerEXT(
        @VkHandleRef("VkInstance") instance: Long,
        @VkHandleRef("VkDebugUtilsMessengerEXT") debugUtilsMessenger: Long,
    )

    /** Records the start of a render pass on a framebuffer. */
    actual external fun vkCmdBeginRenderPass(
        @VkHandleRef("VkCommandBuffer") commandBuffer: Long,
        renderPassBeginInfo: VkRenderPassBeginInfo,
        contents: VkSubpassContents,
    )

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
