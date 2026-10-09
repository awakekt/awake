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
 * Phase 1d Vulkan API surface generated via jni-binding-generator (vendored in
 * tools/jni-binding-generator), not the legacy awake-vulkan-generator that backs
 * com.awakekt.awake.vulkan.Vulkan. Kept in a separate package/object
 * deliberately: the generateJniBindings Gradle task uses `--package-filter` scoped to
 * this package, so it never touches the legacy Vulkan.kt (which has several return/param
 * shapes jni-binding-generator doesn't support as *function-level* types yet, e.g.
 * `Array<VkLayerProperties>` as a return type — only as a struct *field*, which is what
 * Phase 1a's D10 fix actually added). See docs/decisions/D10-codegen-derisk-findings.md.
 *
 * `vkMapMemory`'s natural signature returns a raw pointer/`java.nio.ByteBuffer`, but
 * `java.nio` doesn't exist on Kotlin/Native — it can't be a commonMain `expect` type across
 * every platform. Instead of hand-writing a whole separate map/unmap object,
 * `writeBufferMemoryFloats` does map→memcpy→unmap as one call taking a plain `FloatArray`
 * (universal across every KMP target, and already in jni-binding-generator's supported-type
 * table) — a safer API besides (no manual unmap-forgetting lifecycle bug possible) and one
 * that still fits the auto-generated model with a hand-written native body, exactly like
 * `vkCreateBuffer`.
 */
expect object VulkanBuffers {
    /**
     * Creates a buffer. It has no memory until one is bound with [vkBindBufferMemory].
     *
     * @param device The logical device to create the buffer on.
     * @param createInfo The size in bytes, usage flags and sharing mode.
     * @return The new `VkBuffer` handle.
     */
    fun vkCreateBuffer(device: Long, createInfo: VkBufferCreateInfo): Long

    /**
     * Destroys a buffer. It must no longer be used by pending GPU work, and its memory is freed
     * separately with [vkFreeMemory].
     *
     * @param device The logical device that created the buffer.
     * @param buffer The buffer to destroy.
     */
    fun vkDestroyBuffer(device: Long, buffer: Long)

    /**
     * Returns the size, alignment and acceptable memory types a buffer needs from its backing
     * memory.
     *
     * @param device The logical device that owns the buffer.
     * @param buffer The buffer to query.
     * @return The requirements; `memoryTypeBits` has bit `i` set when memory type `i` is usable.
     */
    fun vkGetBufferMemoryRequirements(device: Long, buffer: Long): VkMemoryRequirements

    /**
     * Finds a memory type that is both allowed by [typeFilter] and has every property in
     * [properties].
     *
     * On desktop and Android a failed search returns -1, while iOS throws an
     * [IllegalStateException]; callers that cannot recover treat both as fatal.
     *
     * @param physicalDevice The physical device whose memory types are searched.
     * @param typeFilter The `memoryTypeBits` mask from a requirements query.
     * @param properties The `VkMemoryPropertyFlags` the type must have, such as host-visible and
     * host-coherent.
     * @return The index of the first matching memory type.
     */
    fun findMemoryType(physicalDevice: Long, typeFilter: Int, properties: Int): Int

    /**
     * Allocates device memory. Drivers cap the number of live allocations, so suballocate where
     * many buffers are needed.
     *
     * @param device The logical device to allocate on.
     * @param allocateInfo The size in bytes and the memory type index.
     * @return The new `VkDeviceMemory` handle.
     */
    fun vkAllocateMemory(device: Long, allocateInfo: VkMemoryAllocateInfo): Long

    /**
     * Frees device memory. Buffers and images bound to it must no longer be used.
     *
     * @param device The logical device that allocated the memory.
     * @param memory The memory to free.
     */
    fun vkFreeMemory(device: Long, memory: Long)

    /**
     * Binds a region of device memory to a buffer. This can be done once per buffer, before its
     * first use.
     *
     * @param device The logical device that owns both objects.
     * @param buffer The buffer to back.
     * @param memory The memory to bind, of a type allowed by the buffer's requirements.
     * @param memoryOffset Byte offset into [memory], a multiple of the buffer's alignment.
     */
    fun vkBindBufferMemory(device: Long, buffer: Long, memory: Long, memoryOffset: Long)

    /**
     * Maps [memory], copies [data] into it at [offset], and unmaps it again.
     *
     * The memory must be host-visible, and host-coherent unless the caller flushes it. [data] must
     * not be empty, because mapping a zero-byte range is invalid.
     *
     * @param device The logical device that owns the memory.
     * @param memory The host-visible memory to write.
     * @param offset Byte offset into [memory] to start writing at.
     * @param data The floats to copy, written in native byte order.
     */
    fun writeBufferMemoryFloats(device: Long, memory: Long, offset: Long, data: FloatArray)

    /** Same map->memcpy->unmap pattern as [writeBufferMemoryFloats], for raw byte data
     * (e.g. texture pixels) instead of float uniform/vertex data. */
    fun writeBufferMemoryBytes(device: Long, memory: Long, offset: Long, data: ByteArray)

    /** Inverse of [writeBufferMemoryBytes] -- map->memcpy-out->unmap `size` bytes starting at
     * `offset` from `device`'s `memory`. Used for offscreen render-target readback, reading out
     * a HOST_VISIBLE staging buffer after `vkCmdCopyImageToBuffer` fills it. */
    fun readBufferMemoryBytes(device: Long, memory: Long, offset: Long, size: Int): ByteArray

    /** `bindingCount` is implicit (`buffers.size`); `offsets` must be the same size. */
    fun vkCmdBindVertexBuffers(
        commandBuffer: Long,
        firstBinding: Int,
        buffers: LongArray,
        offsets: LongArray,
    )

    /** `indexType` uses the plain-`Int` [com.awakekt.awake.vulkan.models.info.VkIndexType] values. */
    fun vkCmdBindIndexBuffer(commandBuffer: Long, buffer: Long, offset: Long, indexType: Int)

    /** Single-region copy (`srcOffset`/`dstOffset` both 0) -- the staging-buffer upload
     * pattern (a HOST_VISIBLE staging buffer written via [writeBufferMemoryFloats]/
     * [writeBufferMemoryBytes], then copied into a DEVICE_LOCAL destination buffer) never
     * needs more than one region, same simplification as [VulkanImages.vkTransitionImageLayout]. */
    fun vkCmdCopyBuffer(commandBuffer: Long, srcBuffer: Long, dstBuffer: Long, size: Long)

    /**
     * Records an indexed draw using the bound index and vertex buffers.
     *
     * @param commandBuffer The command buffer being recorded.
     * @param indexCount Number of indices to draw.
     * @param instanceCount Number of instances to draw.
     * @param firstIndex Offset, in indices, of the first index read.
     * @param vertexOffset Value added to each index before it reads a vertex.
     * @param firstInstance Instance ID of the first instance.
     */
    fun vkCmdDrawIndexed(
        commandBuffer: Long,
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    )

    /** Blocks until all queues on [device] are idle. Used to fully serialize frames so a
     * single (not per-frame-in-flight) uniform buffer can be safely rewritten every frame
     * without racing the GPU's read of the previous frame -- a deliberate simplification;
     * see the MVP-matrix uniform buffer usage in the demo for the full rationale. */
    fun vkDeviceWaitIdle(device: Long)
}
