/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.models.VkMemoryRequirements
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

// Phase 1b (desktop native build) has not landed yet — see docs/mvp-plan.md.
/**
 * Desktop JVM actual of [VulkanBuffers]. Each function is a JNI `external` declaration backed by
 * the generated wrapper in the `awake-vulkan` library: a zero handle throws [IllegalStateException]
 * and a failed Vulkan call throws a [com.awakekt.awake.vulkan.utils.VkResultException]. The library
 * is loaded by [com.awakekt.awake.vulkan.Vulkan], so use that first.
 */
actual object VulkanBuffers {
    /** Creates a buffer. It has no memory until one is bound with [vkBindBufferMemory]. */
    actual external fun vkCreateBuffer(device: Long, createInfo: VkBufferCreateInfo): Long

    /**
     * Destroys a buffer. It must no longer be used by pending GPU work, and its memory is freed
     * separately with [vkFreeMemory].
     */
    actual external fun vkDestroyBuffer(device: Long, buffer: Long)

    /**
     * Returns the size, alignment and acceptable memory types a buffer needs from its backing
     * memory.
     */
    actual external fun vkGetBufferMemoryRequirements(device: Long, buffer: Long): VkMemoryRequirements

    /**
     * Finds a memory type that is both allowed by [typeFilter] and has every property in
     * [properties].
     *
     * On desktop and Android a failed search returns -1, while iOS throws an
     * [IllegalStateException]; callers that cannot recover treat both as fatal.
     */
    actual external fun findMemoryType(physicalDevice: Long, typeFilter: Int, properties: Int): Int

    /**
     * Allocates device memory. Drivers cap the number of live allocations, so suballocate where
     * many buffers are needed.
     */
    actual external fun vkAllocateMemory(device: Long, allocateInfo: VkMemoryAllocateInfo): Long

    /** Frees device memory. Buffers and images bound to it must no longer be used. */
    actual external fun vkFreeMemory(device: Long, memory: Long)

    /**
     * Binds a region of device memory to a buffer. This can be done once per buffer, before its
     * first use.
     */
    actual external fun vkBindBufferMemory(device: Long, buffer: Long, memory: Long, memoryOffset: Long)

    /**
     * Maps [memory], copies [data] into it at [offset], and unmaps it again.
     *
     * The memory must be host-visible, and host-coherent unless the caller flushes it. [data] must
     * not be empty, because mapping a zero-byte range is invalid.
     */
    actual external fun writeBufferMemoryFloats(device: Long, memory: Long, offset: Long, data: FloatArray)

    /**
     * Same map->memcpy->unmap pattern as [writeBufferMemoryFloats], for raw byte data (e.g. texture
     * pixels) instead of float uniform/vertex data.
     */
    actual external fun writeBufferMemoryBytes(device: Long, memory: Long, offset: Long, data: ByteArray)

    /**
     * Inverse of [writeBufferMemoryBytes] -- map->memcpy-out->unmap `size` bytes starting at
     * `offset` from `device`'s `memory`. Used for offscreen render-target readback, reading out a
     * HOST_VISIBLE staging buffer after `vkCmdCopyImageToBuffer` fills it.
     */
    actual external fun readBufferMemoryBytes(device: Long, memory: Long, offset: Long, size: Int): ByteArray

    /** `bindingCount` is implicit (`buffers.size`); `offsets` must be the same size. */
    actual external fun vkCmdBindVertexBuffers(
        commandBuffer: Long,
        firstBinding: Int,
        buffers: LongArray,
        offsets: LongArray,
    )

    /**
     * `indexType` uses the plain-`Int` [com.awakekt.awake.vulkan.models.info.VkIndexType] values.
     */
    actual external fun vkCmdBindIndexBuffer(commandBuffer: Long, buffer: Long, offset: Long, indexType: Int)

    /**
     * Single-region copy (`srcOffset`/`dstOffset` both 0) -- the staging-buffer upload pattern (a
     * HOST_VISIBLE staging buffer written via [writeBufferMemoryFloats]/ [writeBufferMemoryBytes],
     * then copied into a DEVICE_LOCAL destination buffer) never needs more than one region, same
     * simplification as [VulkanImages.vkTransitionImageLayout].
     */
    actual external fun vkCmdCopyBuffer(commandBuffer: Long, srcBuffer: Long, dstBuffer: Long, size: Long)

    /** Records an indexed draw using the bound index and vertex buffers. */
    actual external fun vkCmdDrawIndexed(
        commandBuffer: Long,
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    )

    /**
     * Blocks until all queues on [device] are idle. Used to fully serialize frames so a single (not
     * per-frame-in-flight) uniform buffer can be safely rewritten every frame without racing the
     * GPU's read of the previous frame -- a deliberate simplification; see the MVP-matrix uniform
     * buffer usage in the demo for the full rationale.
     */
    actual external fun vkDeviceWaitIdle(device: Long)
}
