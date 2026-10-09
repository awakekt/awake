/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.enums

/**
 * The result code Vulkan commands return (`VkResult`): zero or positive for success statuses,
 * negative for errors.
 *
 * @property value The raw integer value Vulkan uses for this constant.
 */
enum class VkResult(override val value: Int) : VkEnum {
    /** The command completed successfully. */
    VK_SUCCESS(0),

    /** A fence or query has not completed yet. */
    VK_NOT_READY(1),

    /** A wait did not finish within the given time. */
    VK_TIMEOUT(2),

    /** An event is signalled. */
    VK_EVENT_SET(3),

    /** An event is unsignalled. */
    VK_EVENT_RESET(4),

    /** A return array was too small for the result. */
    VK_INCOMPLETE(5),

    /** A host memory allocation failed. */
    VK_ERROR_OUT_OF_HOST_MEMORY(-1),

    /** A device memory allocation failed. */
    VK_ERROR_OUT_OF_DEVICE_MEMORY(-2),

    /** Initialization of an object could not be completed. */
    VK_ERROR_INITIALIZATION_FAILED(-3),

    /** The logical or physical device was lost. */
    VK_ERROR_DEVICE_LOST(-4),

    /** Mapping of a memory object failed. */
    VK_ERROR_MEMORY_MAP_FAILED(-5),

    /** A requested layer is not present or could not be loaded. */
    VK_ERROR_LAYER_NOT_PRESENT(-6),

    /** A requested extension is not supported. */
    VK_ERROR_EXTENSION_NOT_PRESENT(-7),

    /** A requested feature is not supported. */
    VK_ERROR_FEATURE_NOT_PRESENT(-8),

    /** The requested Vulkan version is not supported by the driver or is otherwise incompatible. */
    VK_ERROR_INCOMPATIBLE_DRIVER(-9),

    /** Too many objects of the type have already been created. */
    VK_ERROR_TOO_MANY_OBJECTS(-10),

    /** A requested format is not supported on this device. */
    VK_ERROR_FORMAT_NOT_SUPPORTED(-11),

    /** A pool allocation failed because of fragmentation. */
    VK_ERROR_FRAGMENTED_POOL(-12),

    /** An unknown error occurred. */
    VK_ERROR_UNKNOWN(-13),

    /** A pool memory allocation failed. */
    VK_ERROR_OUT_OF_POOL_MEMORY(-1000069000),

    /** An external handle is not a valid handle of the specified type. */
    VK_ERROR_INVALID_EXTERNAL_HANDLE(-1000072003),

    /** A descriptor pool creation failed because of fragmentation. */
    VK_ERROR_FRAGMENTATION(-1000161000),

    /**
     * A buffer creation or memory allocation failed because the requested address is not available.
     */
    VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS(-1000257000),

    /**
     * A requested pipeline creation would have required compilation, but the application asked for
     * compilation not to happen.
     */
    VK_PIPELINE_COMPILE_REQUIRED(1000297000),

    /** A surface is no longer available. */
    VK_ERROR_SURFACE_LOST_KHR(-1000000000),

    /** The requested window is already in use by Vulkan or another API. */
    VK_ERROR_NATIVE_WINDOW_IN_USE_KHR(-1000000001),

    /**
     * A swapchain no longer matches the surface properties exactly but can still present
     * successfully.
     */
    VK_SUBOPTIMAL_KHR(1000001003),

    /** A surface changed so that the swapchain is incompatible and must be recreated. */
    VK_ERROR_OUT_OF_DATE_KHR(-1000001004),

    /** The display used by a swapchain does not use the same presentable image layout. */
    VK_ERROR_INCOMPATIBLE_DISPLAY_KHR(-1000003001),

    /** A command failed validation. */
    VK_ERROR_VALIDATION_FAILED_EXT(-1000011001),

    /** One or more shaders failed to compile or link. */
    VK_ERROR_INVALID_SHADER_NV(-1000012000),

    /** An image's DRM format modifier plane layout is invalid. */
    VK_ERROR_INVALID_DRM_FORMAT_MODIFIER_PLANE_LAYOUT_EXT(-1000158000),

    /** The caller lacks the privilege to request the operation, such as a global queue priority. */
    VK_ERROR_NOT_PERMITTED_KHR(-1000174001),

    /** An operation on a swapchain failed because it lost exclusive full-screen access. */
    VK_ERROR_FULL_SCREEN_EXCLUSIVE_MODE_LOST_EXT(-1000255000),

    /**
     * A deferred operation is not complete, but there is currently no work for the calling thread.
     */
    VK_THREAD_IDLE_KHR(1000268000),

    /**
     * A deferred operation is not complete, but there is no work remaining to assign to more
     * threads.
     */
    VK_THREAD_DONE_KHR(1000268001),

    /** A deferred operation was requested and at least some of the work was deferred. */
    VK_OPERATION_DEFERRED_KHR(1000268002),

    /** A deferred operation was requested and no operations were deferred. */
    VK_OPERATION_NOT_DEFERRED_KHR(1000268003),

    /** Alias of [VK_ERROR_OUT_OF_POOL_MEMORY]; both names carry the same value. */
    VK_ERROR_OUT_OF_POOL_MEMORY_KHR(VK_ERROR_OUT_OF_POOL_MEMORY.value),

    /** Alias of [VK_ERROR_INVALID_EXTERNAL_HANDLE]; both names carry the same value. */
    VK_ERROR_INVALID_EXTERNAL_HANDLE_KHR(VK_ERROR_INVALID_EXTERNAL_HANDLE.value),

    /** Alias of [VK_ERROR_FRAGMENTATION]; both names carry the same value. */
    VK_ERROR_FRAGMENTATION_EXT(VK_ERROR_FRAGMENTATION.value),

    /** Alias of [VK_ERROR_NOT_PERMITTED_KHR]; both names carry the same value. */
    VK_ERROR_NOT_PERMITTED_EXT(VK_ERROR_NOT_PERMITTED_KHR.value),

    /** Alias of [VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS]; both names carry the same value. */
    VK_ERROR_INVALID_DEVICE_ADDRESS_EXT(VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS.value),

    /** Alias of [VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS]; both names carry the same value. */
    VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS_KHR(VK_ERROR_INVALID_OPAQUE_CAPTURE_ADDRESS.value),

    /** Alias of [VK_PIPELINE_COMPILE_REQUIRED]; both names carry the same value. */
    VK_PIPELINE_COMPILE_REQUIRED_EXT(VK_PIPELINE_COMPILE_REQUIRED.value),

    /** Alias of [VK_PIPELINE_COMPILE_REQUIRED]; both names carry the same value. */
    VK_ERROR_PIPELINE_COMPILE_REQUIRED_EXT(VK_PIPELINE_COMPILE_REQUIRED.value),

    /** Sentinel that forces the C enum to 32 bits; it is never a valid value. */
    VK_RESULT_MAX_ENUM(0x7FFFFFFF),
}
