/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.vulkan.Version.Companion.vkVersion
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.models.VkMemoryRequirements
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.utils.VkResultException

/**
 * Shared Vulkan memory-placement helpers:
 * - Mobile TBDR transient attachments prefer `DEVICE_LOCAL | LAZILY_ALLOCATED` (zero physical DRAM
 *   allocation / zero tile writeback on Adreno & Mali), falling back to dedicated `DEVICE_LOCAL`.
 * - Sampled render targets and depth arrays use Vulkan 1.1 dedicated allocation when the driver
 *   reports `prefersDedicatedAllocation || requiresDedicatedAllocation` (enabling UBWC/AFBC/DCC).
 * - Hot CPU-written / GPU-read uniform & dynamic buffers prefer `DEVICE_LOCAL | HOST_VISIBLE |
 *   HOST_COHERENT` on ReBAR/SAM and mobile UMA heaps, falling back cleanly on legacy 256 MB BAR
 *   exhaustion or absent ReBAR.
 * - CPU pixel readback buffers prefer `HOST_VISIBLE | HOST_COHERENT | HOST_CACHED`.
 */
internal const val DEFAULT_REBAR_MIN_HEAP_BYTES: Long = 512L * 1024L * 1024L
internal const val SMALL_UBO_REBAR_MIN_HEAP_BYTES: Long = 0L

private const val HOST_VISIBLE_COHERENT =
    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
        VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT

private const val HOST_VISIBLE_COHERENT_CACHED =
    HOST_VISIBLE_COHERENT or VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_CACHED_BIT

private const val DEVICE_LOCAL_LAZY =
    VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT or
        VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT

/**
 * Allocates memory for a `VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT` image (MSAA colour or scene
 * depth with `storeOp = DONT_CARE`). Prefers `DEVICE_LOCAL | LAZILY_ALLOCATED` on TBDR mobile GPUs
 * and falls back to driver-optimal dedicated `DEVICE_LOCAL` on desktop GPUs.
 */
internal fun GraphicsDevice.allocateTransientAttachmentMemory(
    image: Long,
    requirements: VkMemoryRequirements,
): Long {
    val lazyType = VulkanBuffers.findPreferredMemoryType(
        physicalDevice,
        requirements.memoryTypeBits,
        DEVICE_LOCAL_LAZY,
        DEVICE_LOCAL_LAZY,
    )
    if (lazyType >= 0) {
        return VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(requirements.size, lazyType),
        )
    }
    return allocateDeviceImageMemory(image, requirements)
}

/**
 * Allocates `DEVICE_LOCAL` memory for a render target or depth image, using Vulkan 1.1
 * `VkMemoryDedicatedAllocateInfo` when the driver prefers or requires a dedicated allocation.
 */
internal fun GraphicsDevice.allocateDeviceImageMemory(
    image: Long,
    requirements: VkMemoryRequirements,
): Long {
    val memoryType = VulkanBuffers.findMemoryType(
        physicalDevice,
        requirements.memoryTypeBits,
        VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
    )
    return VulkanBuffers.vkAllocateDedicatedImageMemory(
        device = device,
        allocationSize = requirements.size,
        memoryTypeIndex = memoryType,
        image = image,
        deviceApiVersion = effectiveApiVersion.vkVersion,
    )
}

/**
 * Allocates `HOST_VISIBLE | HOST_COHERENT` memory for GPU-read buffers (UBOs, instance buffers,
 * dynamic vertex/index buffers). Prefers `DEVICE_LOCAL | HOST_VISIBLE | HOST_COHERENT` on ReBAR/UMA
 * heaps of at least [minRebarHeapBytes] bytes, transparently falling back to plain host-visible
 * memory if unavailable or if a small BAR heap runs out of space.
 */
internal fun GraphicsDevice.allocateHostVisibleBufferMemory(
    requirements: VkMemoryRequirements,
    minRebarHeapBytes: Long = DEFAULT_REBAR_MIN_HEAP_BYTES,
): Long {
    val fastType = VulkanBuffers.findDeviceLocalHostVisibleMemoryType(
        physicalDevice,
        requirements.memoryTypeBits,
        minRebarHeapBytes,
    )
    if (fastType >= 0) {
        try {
            return VulkanBuffers.vkAllocateMemory(
                device,
                VkMemoryAllocateInfo(requirements.size, fastType),
            )
        } catch (error: VkResultException) {
            if (error.result != VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY) throw error
        }
    }
    val fallbackType = VulkanBuffers.findMemoryType(
        physicalDevice,
        requirements.memoryTypeBits,
        HOST_VISIBLE_COHERENT,
    )
    return VulkanBuffers.vkAllocateMemory(
        device,
        VkMemoryAllocateInfo(requirements.size, fallbackType),
    )
}

/**
 * Allocates `HOST_VISIBLE | HOST_COHERENT` staging memory for CPU readback (such as `readPixels`),
 * preferring `HOST_CACHED` when available so CPU reads avoid uncached write-combined stalls.
 */
internal fun GraphicsDevice.allocateReadbackStagingMemory(requirements: VkMemoryRequirements): Long {
    val memoryType = VulkanBuffers.findPreferredMemoryType(
        physicalDevice,
        requirements.memoryTypeBits,
        HOST_VISIBLE_COHERENT_CACHED,
        HOST_VISIBLE_COHERENT,
    )
    return VulkanBuffers.vkAllocateMemory(
        device,
        VkMemoryAllocateInfo(requirements.size, memoryType),
    )
}
