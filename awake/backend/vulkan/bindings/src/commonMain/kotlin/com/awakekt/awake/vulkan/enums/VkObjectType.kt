/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The type of a Vulkan object, as named in debug utils messages (`VkObjectType`).
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkObjectType(val value: Int) {
    /** The object type is unknown or not given. */
    VK_OBJECT_TYPE_UNKNOWN(0),

    /** The type of a `VkInstance` handle. */
    VK_OBJECT_TYPE_INSTANCE(1),

    /** The type of a `VkPhysicalDevice` handle. */
    VK_OBJECT_TYPE_PHYSICAL_DEVICE(2),

    /** The type of a `VkDevice` handle. */
    VK_OBJECT_TYPE_DEVICE(3),

    /** The type of a `VkQueue` handle. */
    VK_OBJECT_TYPE_QUEUE(4),

    /** The type of a `VkSemaphore` handle. */
    VK_OBJECT_TYPE_SEMAPHORE(5),

    /** The type of a `VkCommandBuffer` handle. */
    VK_OBJECT_TYPE_COMMAND_BUFFER(6),

    /** The type of a `VkFence` handle. */
    VK_OBJECT_TYPE_FENCE(7),

    /** The type of a `VkDeviceMemory` handle. */
    VK_OBJECT_TYPE_DEVICE_MEMORY(8),

    /** The type of a `VkBuffer` handle. */
    VK_OBJECT_TYPE_BUFFER(9),

    /** The type of a `VkImage` handle. */
    VK_OBJECT_TYPE_IMAGE(10),

    /** The type of a `VkEvent` handle. */
    VK_OBJECT_TYPE_EVENT(11),

    /** The type of a `VkQueryPool` handle. */
    VK_OBJECT_TYPE_QUERY_POOL(12),

    /** The type of a `VkBufferView` handle. */
    VK_OBJECT_TYPE_BUFFER_VIEW(13),

    /** The type of a `VkImageView` handle. */
    VK_OBJECT_TYPE_IMAGE_VIEW(14),

    /** The type of a `VkShaderModule` handle. */
    VK_OBJECT_TYPE_SHADER_MODULE(15),

    /** The type of a `VkPipelineCache` handle. */
    VK_OBJECT_TYPE_PIPELINE_CACHE(16),

    /** The type of a `VkPipelineLayout` handle. */
    VK_OBJECT_TYPE_PIPELINE_LAYOUT(17),

    /** The type of a `VkRenderPass` handle. */
    VK_OBJECT_TYPE_RENDER_PASS(18),

    /** The type of a `VkPipeline` handle. */
    VK_OBJECT_TYPE_PIPELINE(19),

    /** The type of a `VkDescriptorSetLayout` handle. */
    VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT(20),

    /** The type of a `VkSampler` handle. */
    VK_OBJECT_TYPE_SAMPLER(21),

    /** The type of a `VkDescriptorPool` handle. */
    VK_OBJECT_TYPE_DESCRIPTOR_POOL(22),

    /** The type of a `VkDescriptorSet` handle. */
    VK_OBJECT_TYPE_DESCRIPTOR_SET(23),

    /** The type of a `VkFramebuffer` handle. */
    VK_OBJECT_TYPE_FRAMEBUFFER(24),

    /** The type of a `VkCommandPool` handle. */
    VK_OBJECT_TYPE_COMMAND_POOL(25),

    /** The type of a `VkSamplerYcbcrConversion` handle. */
    VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION(1000156000),

    /** The type of a `VkDescriptorUpdateTemplate` handle. */
    VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE(1000085000),

    /** The type of a `VkPrivateDataSlot` handle. */
    VK_OBJECT_TYPE_PRIVATE_DATA_SLOT(1000295000),

    /** The type of a `VkSurfaceKHR` handle. */
    VK_OBJECT_TYPE_SURFACE_KHR(1000000000),

    /** The type of a `VkSwapchainKHR` handle. */
    VK_OBJECT_TYPE_SWAPCHAIN_KHR(1000001000),

    /** The type of a `VkDisplayKHR` handle. */
    VK_OBJECT_TYPE_DISPLAY_KHR(1000002000),

    /** The type of a `VkDisplayModeKHR` handle. */
    VK_OBJECT_TYPE_DISPLAY_MODE_KHR(1000002001),

    /** The type of a `VkDebugReportCallbackEXT` handle. */
    VK_OBJECT_TYPE_DEBUG_REPORT_CALLBACK_EXT(1000011000),

    /** The type of a `VkVideoSessionKHR` handle. */
    VK_OBJECT_TYPE_VIDEO_SESSION_KHR(1000023000),

    /** The type of a `VkVideoSessionParametersKHR` handle. */
    VK_OBJECT_TYPE_VIDEO_SESSION_PARAMETERS_KHR(1000023001),

    /** The type of a `VkCuModuleNVX` handle. */
    VK_OBJECT_TYPE_CU_MODULE_NVX(1000029000),

    /** The type of a `VkCuFunctionNVX` handle. */
    VK_OBJECT_TYPE_CU_FUNCTION_NVX(1000029001),

    /** The type of a `VkDebugUtilsMessengerEXT` handle. */
    VK_OBJECT_TYPE_DEBUG_UTILS_MESSENGER_EXT(1000128000),

    /** The type of a `VkAccelerationStructureKHR` handle. */
    VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_KHR(1000150000),

    /** The type of a `VkValidationCacheEXT` handle. */
    VK_OBJECT_TYPE_VALIDATION_CACHE_EXT(1000160000),

    /** The type of a `VkAccelerationStructureNV` handle. */
    VK_OBJECT_TYPE_ACCELERATION_STRUCTURE_NV(1000165000),

    /** The type of a `VkPerformanceConfigurationINTEL` handle. */
    VK_OBJECT_TYPE_PERFORMANCE_CONFIGURATION_INTEL(1000210000),

    /** The type of a `VkDeferredOperationKHR` handle. */
    VK_OBJECT_TYPE_DEFERRED_OPERATION_KHR(1000268000),

    /** The type of a `VkIndirectCommandsLayoutNV` handle. */
    VK_OBJECT_TYPE_INDIRECT_COMMANDS_LAYOUT_NV(1000277000),

    /** The type of a `VkBufferCollectionFUCHSIA` handle. */
    VK_OBJECT_TYPE_BUFFER_COLLECTION_FUCHSIA(1000366000),

    /** Alias of [VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE]; both names carry the same value. */
    VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE_KHR(VK_OBJECT_TYPE_DESCRIPTOR_UPDATE_TEMPLATE.value),

    /** Alias of [VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION]; both names carry the same value. */
    VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION_KHR(VK_OBJECT_TYPE_SAMPLER_YCBCR_CONVERSION.value),

    /** Alias of [VK_OBJECT_TYPE_PRIVATE_DATA_SLOT]; both names carry the same value. */
    VK_OBJECT_TYPE_PRIVATE_DATA_SLOT_EXT(VK_OBJECT_TYPE_PRIVATE_DATA_SLOT.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_OBJECT_TYPE_MAX_ENUM(0x7FFFFFFF),
}
