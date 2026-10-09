/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

/** Size in bytes of a Vulkan UUID, such as a pipeline cache UUID. */
const val VK_UUID_SIZE = 16

/** Attachment index that marks an attachment reference as unused; `~0U` in C. */
const val VK_ATTACHMENT_UNUSED = -1

/** The raw `VkBool32` value for false. */
const val VK_FALSE = 0

/** Value for a sampler's maximum LOD that turns off clamping to the image's last mip level. */
const val VK_LOD_CLAMP_NONE = 1000.0f

/** Queue family index meaning no queue family ownership transfer; `~0U` in C. */
const val VK_QUEUE_FAMILY_IGNORED = -1

/** Layer count meaning every array layer from the base layer to the last; `~0U` in C. */
const val VK_REMAINING_ARRAY_LAYERS = -1

/**
 * Level count meaning every mip level from the base level to the last; `~0U` in C. MoltenVK
 * transitions only level 0 for it, so pass real counts.
 */
const val VK_REMAINING_MIP_LEVELS = -1

/** Subpass index in a dependency that stands for the work outside the render pass; `~0U` in C. */
const val VK_SUBPASS_EXTERNAL = -1

/** The raw `VkBool32` value for true. */
const val VK_TRUE = 1

/** Size meaning from the given offset to the end of the resource; `~0ULL` in C. */
const val VK_WHOLE_SIZE = -1

/** Largest number of memory types a physical device can report. */
const val VK_MAX_MEMORY_TYPES = 32

/** Largest number of memory heaps a physical device can report. */
const val VK_MAX_MEMORY_HEAPS = 16

/** Length of the fixed array that holds a physical device name. */
const val VK_MAX_PHYSICAL_DEVICE_NAME_SIZE = 256

/** Length of the fixed array that holds an extension or layer name. */
const val VK_MAX_EXTENSION_NAME_SIZE = 256

/** Length of the fixed array that holds a layer description. */
const val VK_MAX_DESCRIPTION_SIZE = 256

typealias VkBool32 = Boolean
typealias VkDeviceAddress = Long
typealias VkDeviceSize = Long
typealias VkFlags = Int
typealias VkSampleMask = Int

typealias VkHandle = Long

/**
 * The Vulkan instance and device extensions this binding layer refers to by name.
 *
 * @property extensionName The exact extension string Vulkan expects, for example
 * `VK_KHR_swapchain`.
 */
enum class VulkanExtension(val extensionName: String) {
    /** Presents rendered images to a surface; every windowed renderer needs it on the device. */
    VK_KHR_SWAPCHAIN("VK_KHR_swapchain"),

    /** Small additions folded into core 1.1, including negative viewport heights, which flip Y. */
    VK_KHR_MAINTENANCE1("VK_KHR_maintenance1"),

    /** Exposes the `BaseInstance`, `BaseVertex` and `DrawIndex` built-ins to shaders. */
    VK_KHR_SHADER_DRAW_PARAMETERS("VK_KHR_shader_draw_parameters"),

    /** Queries features and properties through extensible structure chains. */
    VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES2("VK_KHR_get_physical_device_properties2"),

    /** Reports which external memory handle types a device can import or export. */
    VK_KHR_EXTERNAL_MEMORY_CAPABILITIES("VK_KHR_external_memory_capabilities"),

    /** Lets device memory be shared with other APIs or processes. */
    VK_KHR_EXTERNAL_MEMORY("VK_KHR_external_memory"),

    /** Imports and exports device memory as POSIX file descriptors. */
    VK_KHR_EXTERNAL_MEMORY_FD("VK_KHR_external_memory_fd"),

    /** Reports which external semaphore handle types a device can import or export. */
    VK_KHR_EXTERNAL_SEMAPHORE_CAPABILITIES("VK_KHR_external_semaphore_capabilities"),

    /** Lets semaphores be shared with other APIs or processes. */
    VK_KHR_EXTERNAL_SEMAPHORE("VK_KHR_external_semaphore"),

    /** Imports and exports semaphores as POSIX file descriptors. */
    VK_KHR_EXTERNAL_SEMAPHORE_FD("VK_KHR_external_semaphore_fd"),

    /** Defines `VkSurfaceKHR`; the instance extension every presentation path starts from. */
    VK_KHR_SURFACE("VK_KHR_surface"),

    /** Creates a surface from an Android native window. */
    VK_KHR_ANDROID_SURFACE("VK_KHR_android_surface"),

    /** Creates a surface from an X11 window through XCB. */
    VK_KHR_XCB_SURFACE("VK_KHR_xcb_surface"),

    /** Creates a surface from an X11 window through Xlib. */
    VK_KHR_XLIB_SURFACE("VK_KHR_xlib_surface"),

    /** Creates a surface from a Win32 window. */
    VK_KHR_WIN32_SURFACE("VK_KHR_win32_surface"),
    ;

    /** Lookup of an extension by its Vulkan name. */
    companion object {
        /**
         * Returns the entry whose [extensionName] equals [name], or `null` when this enum does not
         * list it.
         *
         * @param name The extension string as Vulkan reports it.
         */
        fun fromString(name: String): VulkanExtension? =
            values().find { it.extensionName == name }
    }
}
