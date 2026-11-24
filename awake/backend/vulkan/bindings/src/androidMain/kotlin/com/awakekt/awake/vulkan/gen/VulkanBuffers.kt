/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.models.VkMemoryRequirements
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/**
 * Android actual of [VulkanBuffers]. Each function is a JNI `external` declaration backed by the
 * generated wrapper in the `awake-vulkan` library: a zero handle throws [IllegalStateException] and
 * a failed Vulkan call throws a [com.awakekt.awake.vulkan.utils.VkResultException]. The library is
 * loaded by [com.awakekt.awake.vulkan.Vulkan], so use that first.
 */
@Suppress("TooManyFunctions")
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
    actual external fun vkGetBufferMemoryRequirements(
        device: Long,
        buffer: Long,
    ): VkMemoryRequirements

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
    actual external fun vkBindBufferMemory(
        device: Long,
        buffer: Long,
        memory: Long,
        memoryOffset: Long,
    )

    /**
     * Maps [memory], copies [data] into it at [offset], and unmaps it again.
     *
     * The memory must be host-visible, and host-coherent unless the caller flushes it. [data] must
     * not be empty, because mapping a zero-byte range is invalid.
     */
    actual external fun writeBufferMemoryFloats(
        device: Long,
        memory: Long,
        offset: Long,
        data: FloatArray,
    )

    /**
     * Maps [memory], copies [count] floats of [data] from [dataOffset] into it at [offset], and
     * unmaps it again: part of an array written in place, with no copy of the range first.
     */
    actual fun writeBufferMemoryFloats(device: Long, memory: Long, offset: Long, data: FloatArray, dataOffset: Int, count: Int) {
        requireFloatRange(data.size, dataOffset, count)
        writeBufferMemoryFloatRange(device, memory, offset, data, dataOffset, count)
    }

    // Its own JNI name: an overloaded native method would resolve to the short symbol of the
    // four-argument one, and the JVM would call it with the wrong arguments.
    private external fun writeBufferMemoryFloatRange(device: Long, memory: Long, offset: Long, data: FloatArray, dataOffset: Int, count: Int)

    /**
     * Same map->memcpy->unmap pattern as [writeBufferMemoryFloats], for raw byte data (e.g. texture
     * pixels) instead of float uniform/vertex data.
     */
    actual external fun writeBufferMemoryBytes(
        device: Long,
        memory: Long,
        offset: Long,
        data: ByteArray,
    )

    /**
     * Inverse of [writeBufferMemoryBytes] -- map->memcpy-out->unmap `size` bytes starting at
     * `offset` from `device`'s `memory`. Used for offscreen render-target readback, reading out a
     * HOST_VISIBLE staging buffer after `vkCmdCopyImageToBuffer` fills it.
     */
    actual external fun readBufferMemoryBytes(
        device: Long,
        memory: Long,
        offset: Long,
        size: Int,
    ): ByteArray

    /** Persistently maps [size] bytes of host-visible [memory] starting at [offset]. */
    actual external fun vkMapMemory(device: Long, memory: Long, offset: Long, size: Long): Long

    /** Unmaps host-visible [memory] previously mapped with [vkMapMemory]. */
    actual external fun vkUnmapMemory(device: Long, memory: Long)

    /** Copies [data] into persistently mapped memory at [mappedPtr] + [offset] bytes. */
    actual external fun writeMappedMemoryFloats(mappedPtr: Long, offset: Long, data: FloatArray)

    /** Copies [count] floats of [data] from [dataOffset] into persistently mapped memory. */
    actual fun writeMappedMemoryFloats(mappedPtr: Long, offset: Long, data: FloatArray, dataOffset: Int, count: Int) {
        requireFloatRange(data.size, dataOffset, count)
        if (count == 0) return
        writeMappedMemoryFloatRange(mappedPtr, offset, data, dataOffset, count)
    }

    private external fun writeMappedMemoryFloatRange(mappedPtr: Long, offset: Long, data: FloatArray, dataOffset: Int, count: Int)

    /** Copies [count] ints of [data] from [dataOffset] into persistently mapped memory. */
    actual fun writeMappedMemoryInts(mappedPtr: Long, offset: Long, data: IntArray, dataOffset: Int, count: Int) {
        requireArrayRange(data.size, dataOffset, count, "ints")
        if (count == 0) return
        writeMappedMemoryIntRange(mappedPtr, offset, data, dataOffset, count)
    }

    private external fun writeMappedMemoryIntRange(mappedPtr: Long, offset: Long, data: IntArray, dataOffset: Int, count: Int)

    /** Copies [data] into persistently mapped memory at [mappedPtr] + [offset] bytes. */
    actual external fun writeMappedMemoryBytes(mappedPtr: Long, offset: Long, data: ByteArray)

    /** Copies [count] bytes of [data] from [dataOffset] into persistently mapped memory. */
    actual fun writeMappedMemoryBytes(mappedPtr: Long, offset: Long, data: ByteArray, dataOffset: Int, count: Int) {
        requireArrayRange(data.size, dataOffset, count, "bytes")
        if (count == 0) return
        writeMappedMemoryByteRange(mappedPtr, offset, data, dataOffset, count)
    }

    private external fun writeMappedMemoryByteRange(mappedPtr: Long, offset: Long, data: ByteArray, dataOffset: Int, count: Int)

    /** Reads [size] bytes from persistently mapped memory at [mappedPtr] + [offset] bytes. */
    actual external fun readMappedMemoryBytes(mappedPtr: Long, offset: Long, size: Int): ByteArray

    /** Scalar fast path for binding a single vertex buffer with zero heap allocations. */
    actual external fun vkCmdBindVertexBuffer(commandBuffer: Long, binding: Int, buffer: Long, offset: Long)

    /** Finds a memory type matching [preferredProperties], falling back to [fallbackProperties], or -1. */
    actual external fun findPreferredMemoryType(
        physicalDevice: Long,
        typeFilter: Int,
        preferredProperties: Int,
        fallbackProperties: Int,
    ): Int

    /** Finds a `DEVICE_LOCAL | HOST_VISIBLE | HOST_COHERENT` memory type backed by a heap `>= minHeapBytes`, or -1. */
    actual external fun findDeviceLocalHostVisibleMemoryType(
        physicalDevice: Long,
        typeFilter: Int,
        minHeapBytes: Long,
    ): Int

    /** Allocates image memory, using Vulkan 1.1 dedicated allocation when the driver prefers or requires it. */
    actual external fun vkAllocateDedicatedImageMemory(
        device: Long,
        allocationSize: Long,
        memoryTypeIndex: Int,
        image: Long,
        deviceApiVersion: Int,
    ): Long

    /** Records a single-region buffer copy with explicit [srcOffset] and [dstOffset]. */
    @Suppress("LongParameterList")
    actual external fun vkCmdCopyBufferRange(
        commandBuffer: Long,
        srcBuffer: Long,
        dstBuffer: Long,
        srcOffset: Long,
        dstOffset: Long,
        size: Long,
    )

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
