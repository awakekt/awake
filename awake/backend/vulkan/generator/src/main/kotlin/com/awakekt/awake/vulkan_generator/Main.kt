/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan_generator

import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.models.VkAttachmentDescription
import com.awakekt.awake.vulkan.models.VkAttachmentReference
import com.awakekt.awake.vulkan.models.VkClearColorValue
import com.awakekt.awake.vulkan.models.VkClearDepthStencilValue
import com.awakekt.awake.vulkan.models.VkExtensionProperties
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkExtent3D
import com.awakekt.awake.vulkan.models.VkLayerProperties
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkQueueFamilyProperties
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkStencilOpState
import com.awakekt.awake.vulkan.models.VkSubpassDependency
import com.awakekt.awake.vulkan.models.VkSurfaceCapabilitiesKHR
import com.awakekt.awake.vulkan.models.VkSurfaceFormatKHR
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkAndroidSurfaceCreateInfoKHR
import com.awakekt.awake.vulkan.models.info.VkApplicationInfo
import com.awakekt.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkCommandBufferBeginInfo
import com.awakekt.awake.vulkan.models.info.VkCommandBufferInheritanceInfo
import com.awakekt.awake.vulkan.models.info.VkCommandPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkComponentMapping
import com.awakekt.awake.vulkan.models.info.VkDeviceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDeviceQueueCreateInfo
import com.awakekt.awake.vulkan.models.info.VkFenceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkGraphicsPipelineCreateInfo
import com.awakekt.awake.vulkan.models.info.VkImageSubresourceRange
import com.awakekt.awake.vulkan.models.info.VkImageViewCreateInfo
import com.awakekt.awake.vulkan.models.info.VkInstanceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkPresentInfoKHR
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSemaphoreCreateInfo
import com.awakekt.awake.vulkan.models.info.VkShaderModuleCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSubmitInfo
import com.awakekt.awake.vulkan.models.info.VkSubpassDescription
import com.awakekt.awake.vulkan.models.info.VkSwapchainCreateInfoKHR
import com.awakekt.awake.vulkan.models.info.debug.VkDebugUtilsLabelEXT
import com.awakekt.awake.vulkan.models.info.debug.VkDebugUtilsMessengerCallbackDataEXT
import com.awakekt.awake.vulkan.models.info.debug.VkDebugUtilsObjectNameInfoEXT
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineCacheCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineColorBlendAttachmentState
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineColorBlendStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineDepthStencilStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineDynamicStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineInputAssemblyStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineMultisampleStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineRasterizationStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineShaderStageCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineTessellationStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineVertexInputStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPipelineViewportStateCreateInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkPushConstantRange
import com.awakekt.awake.vulkan.models.info.pipeline.VkSpecializationInfo
import com.awakekt.awake.vulkan.models.info.pipeline.VkSpecializationMapEntry
import com.awakekt.awake.vulkan.models.info.pipeline.VkVertexInputAttributeDescription
import com.awakekt.awake.vulkan.models.info.pipeline.VkVertexInputBindingDescription
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceFeatures
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceLimits
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceProperties
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceSparseProperties
import com.awakekt.awake.vulkan_generator.tool.FileWriter
import com.awakekt.awake.vulkan_generator.tool.cmakeListTemplate
import com.awakekt.awake.vulkan_generator.vulkan.createVulkanUtils
import com.awakekt.awake.vulkan_generator.vulkan.generateJavaToVulkanCpp

fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        FileWriter.rootDir = args[0]
    }
    // debug utils
//    generateJavaToVulkanCpp<VkDebugUtilsMessengerCreateInfoEXT>()
    generateJavaToVulkanCpp<VkDebugUtilsLabelEXT>()
    generateJavaToVulkanCpp<VkDebugUtilsObjectNameInfoEXT>()
    generateJavaToVulkanCpp<VkDebugUtilsMessengerCallbackDataEXT>()

    // application
    generateJavaToVulkanCpp<VkApplicationInfo>()
    generateJavaToVulkanCpp<VkInstanceCreateInfo>()
    generateJavaToVulkanCpp<VkPhysicalDeviceSparseProperties>()
    generateJavaToVulkanCpp<VkPhysicalDeviceProperties>()
    generateJavaToVulkanCpp<VkPhysicalDeviceLimits>()
    generateJavaToVulkanCpp<VkPhysicalDeviceFeatures>()
    generateJavaToVulkanCpp<VkQueueFamilyProperties>()
    generateJavaToVulkanCpp<VkDeviceQueueCreateInfo>()
    generateJavaToVulkanCpp<VkDeviceCreateInfo>()
    generateJavaToVulkanCpp<VkExtent3D>()
    generateJavaToVulkanCpp<VkExtent2D>()

    // swapchain
    generateJavaToVulkanCpp<VkShaderModuleCreateInfo>()
    generateJavaToVulkanCpp<VkImageSubresourceRange>()
    generateJavaToVulkanCpp<VkComponentMapping>()
    generateJavaToVulkanCpp<VkImageViewCreateInfo>()
    generateJavaToVulkanCpp<VkSwapchainCreateInfoKHR>()
    // presentation
    generateJavaToVulkanCpp<VkAndroidSurfaceCreateInfoKHR>()
    generateJavaToVulkanCpp<VkSurfaceCapabilitiesKHR>()
    generateJavaToVulkanCpp<VkSurfaceFormatKHR>()

    // pipeline
    generateJavaToVulkanCpp<VkSpecializationMapEntry>()
    generateJavaToVulkanCpp<VkSpecializationInfo>()
    generateJavaToVulkanCpp<VkPipelineShaderStageCreateInfo>()
    generateJavaToVulkanCpp<VkPushConstantRange>()
    generateJavaToVulkanCpp<VkPipelineLayoutCreateInfo>()
    generateJavaToVulkanCpp<VkPipelineCacheCreateInfo>()
    generateJavaToVulkanCpp<VkGraphicsPipelineCreateInfo>()
    // states
    generateJavaToVulkanCpp<VkStencilOpState>()
    generateJavaToVulkanCpp<VkPipelineColorBlendAttachmentState>()

    generateJavaToVulkanCpp<VkPipelineColorBlendStateCreateInfo>()
    generateJavaToVulkanCpp<VkPipelineDepthStencilStateCreateInfo>()
    generateJavaToVulkanCpp<VkPipelineMultisampleStateCreateInfo>()
    generateJavaToVulkanCpp<VkPipelineRasterizationStateCreateInfo>()
    generateJavaToVulkanCpp<VkPipelineTessellationStateCreateInfo>()
    // dynamic state
    generateJavaToVulkanCpp<VkPipelineDynamicStateCreateInfo>()
    // vertex input state
    generateJavaToVulkanCpp<VkVertexInputBindingDescription>()
    generateJavaToVulkanCpp<VkVertexInputAttributeDescription>()
    generateJavaToVulkanCpp<VkPipelineVertexInputStateCreateInfo>()
    // input assembly
    generateJavaToVulkanCpp<VkPipelineInputAssemblyStateCreateInfo>()
    generateJavaToVulkanCpp<VkOffset2D>()
    generateJavaToVulkanCpp<VkRect2D>()
    generateJavaToVulkanCpp<VkViewport>()
    generateJavaToVulkanCpp<VkPipelineViewportStateCreateInfo>()
    // render pass
    generateJavaToVulkanCpp<VkAttachmentReference>()
    generateJavaToVulkanCpp<VkAttachmentDescription>()
    generateJavaToVulkanCpp<VkSubpassDescription>()
    generateJavaToVulkanCpp<VkSubpassDependency>()
    generateJavaToVulkanCpp<VkRenderPassCreateInfo>()

    // frame buffer
    generateJavaToVulkanCpp<VkFramebufferCreateInfo>()

    // command buffers
    generateJavaToVulkanCpp<VkCommandBufferAllocateInfo>()
    generateJavaToVulkanCpp<VkCommandBufferBeginInfo>()
    generateJavaToVulkanCpp<VkCommandPoolCreateInfo>()
    generateJavaToVulkanCpp<VkCommandBufferInheritanceInfo>()

    generateJavaToVulkanCpp<VkClearColorValue.Float32>()
    generateJavaToVulkanCpp<VkClearColorValue.Int32>()
    generateJavaToVulkanCpp<VkClearColorValue.UInt32>()
    generateJavaToVulkanCpp<VkClearDepthStencilValue>()
    generateJavaToVulkanCpp<VkClearColorValue>()
//    generateJavaToVulkanCpp<VkClearValue>()
    generateJavaToVulkanCpp<VkRenderPassBeginInfo>()

    // rendering
    generateJavaToVulkanCpp<VkSemaphoreCreateInfo>()
    generateJavaToVulkanCpp<VkFenceCreateInfo>()

    generateJavaToVulkanCpp<VkSubmitInfo>()
    generateJavaToVulkanCpp<VkPresentInfoKHR>()

    // props
    generateJavaToVulkanCpp<VkExtensionProperties>()
    generateJavaToVulkanCpp<VkLayerProperties>()

    // vulkan awake utils
    createVulkanUtils(Vulkan::class.java)

    println(cmakeListTemplate("awake-backend-vulkan/src/main/cpp/vulkan-kotlin/"))
}
