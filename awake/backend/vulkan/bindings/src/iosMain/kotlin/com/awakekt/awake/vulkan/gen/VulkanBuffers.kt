/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import cnames.structs.VkBuffer_T
import cnames.structs.VkDeviceMemory_T
import cnames.structs.VkDevice_T
import com.awakekt.awake.vulkan.models.VkMemoryRequirements
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.MoltenVK.VK_SUCCESS
import platform.MoltenVK.VkBufferVar
import platform.MoltenVK.VkDeviceMemoryVar
import platform.posix.memcpy
import platform.MoltenVK.VkBufferCopy as NativeVkBufferCopy
import platform.MoltenVK.VkBufferCreateInfo as NativeVkBufferCreateInfo
import platform.MoltenVK.VkMemoryAllocateInfo as NativeVkMemoryAllocateInfo
import platform.MoltenVK.vkAllocateMemory as nativeVkAllocateMemory
import platform.MoltenVK.vkBindBufferMemory as nativeVkBindBufferMemory
import platform.MoltenVK.vkCmdBindIndexBuffer as nativeVkCmdBindIndexBuffer
import platform.MoltenVK.vkCmdBindVertexBuffers as nativeVkCmdBindVertexBuffers
import platform.MoltenVK.vkCmdCopyBuffer as nativeVkCmdCopyBuffer
import platform.MoltenVK.vkCmdDrawIndexed as nativeVkCmdDrawIndexed
import platform.MoltenVK.vkCreateBuffer as nativeVkCreateBuffer
import platform.MoltenVK.vkDestroyBuffer as nativeVkDestroyBuffer
import platform.MoltenVK.vkDeviceWaitIdle as nativeVkDeviceWaitIdle
import platform.MoltenVK.vkFreeMemory as nativeVkFreeMemory
import platform.MoltenVK.vkGetBufferMemoryRequirements as nativeVkGetBufferMemoryRequirements
import platform.MoltenVK.vkGetPhysicalDeviceMemoryProperties as nativeVkGetPhysicalDeviceMemoryProperties
import platform.MoltenVK.vkMapMemory as nativeVkMapMemory
import platform.MoltenVK.vkUnmapMemory as nativeVkUnmapMemory

// Phase 6 (MoltenVK cinterop) is in progress -- see docs/mvp-plan.md.
/**
 * iOS actual of [VulkanBuffers], implemented through MoltenVK cinterop. A failed creation or
 * allocation call throws an [IllegalStateException], and so does a failed memory-type search,
 * instead of returning -1.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalForeignApi::class)
actual object VulkanBuffers {
    /** Creates a buffer. It has no memory until one is bound with [vkBindBufferMemory]. */
    actual fun vkCreateBuffer(device: Long, createInfo: VkBufferCreateInfo): Long = memScoped {
        val nativeCreateInfo = alloc<NativeVkBufferCreateInfo>().apply {
            sType = platform.MoltenVK.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO
            pNext = null
            flags = createInfo.flags.toUInt()
            size = createInfo.size.toULong()
            usage = createInfo.usage.toUInt()
            sharingMode = createInfo.sharingMode.value.toUInt()
            queueFamilyIndexCount = 0u
            pQueueFamilyIndices = null
        }
        val bufferVar = alloc<VkBufferVar>()
        val result = nativeVkCreateBuffer(device.toCPointer(), nativeCreateInfo.ptr, null, bufferVar.ptr)
        check(result == VK_SUCCESS) { "vkCreateBuffer failed: $result" }
        bufferVar.value!!.rawValue.toLong()
    }

    /**
     * Destroys a buffer. It must no longer be used by pending GPU work, and its memory is freed
     * separately with [vkFreeMemory].
     */
    actual fun vkDestroyBuffer(device: Long, buffer: Long) {
        nativeVkDestroyBuffer(device.toCPointer(), buffer.toCPointer<VkBuffer_T>(), null)
    }

    /**
     * Returns the size, alignment and acceptable memory types a buffer needs from its backing
     * memory.
     */
    actual fun vkGetBufferMemoryRequirements(device: Long, buffer: Long): VkMemoryRequirements = memScoped {
        val native = alloc<platform.MoltenVK.VkMemoryRequirements>()
        nativeVkGetBufferMemoryRequirements(device.toCPointer(), buffer.toCPointer<VkBuffer_T>(), native.ptr)
        VkMemoryRequirements(
            size = native.size.toLong(),
            alignment = native.alignment.toLong(),
            memoryTypeBits = native.memoryTypeBits.toInt(),
        )
    }

    /**
     * Finds a memory type that is both allowed by [typeFilter] and has every property in
     * [properties].
     *
     * On desktop and Android a failed search returns -1, while iOS throws an
     * [IllegalStateException]; callers that cannot recover treat both as fatal.
     */
    actual fun findMemoryType(physicalDevice: Long, typeFilter: Int, properties: Int): Int = memScoped {
        val memProps = alloc<platform.MoltenVK.VkPhysicalDeviceMemoryProperties>()
        nativeVkGetPhysicalDeviceMemoryProperties(physicalDevice.toCPointer(), memProps.ptr)
        val count = memProps.memoryTypeCount.toInt()
        for (i in 0 until count) {
            val bitSet = (typeFilter and (1 shl i)) != 0
            val flagsMatch =
                (memProps.memoryTypes[i].propertyFlags.toInt() and properties) == properties
            if (bitSet && flagsMatch) {
                return@memScoped i
            }
        }
        error("findMemoryType: no suitable memory type for filter=$typeFilter properties=$properties")
    }

    /**
     * Allocates device memory. Drivers cap the number of live allocations, so suballocate where
     * many buffers are needed.
     */
    actual fun vkAllocateMemory(device: Long, allocateInfo: VkMemoryAllocateInfo): Long = memScoped {
        val nativeAllocateInfo = alloc<NativeVkMemoryAllocateInfo>().apply {
            sType = platform.MoltenVK.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
            pNext = null
            allocationSize = allocateInfo.allocationSize.toULong()
            memoryTypeIndex = allocateInfo.memoryTypeIndex.toUInt()
        }
        val memoryVar = alloc<VkDeviceMemoryVar>()
        val result = nativeVkAllocateMemory(device.toCPointer(), nativeAllocateInfo.ptr, null, memoryVar.ptr)
        check(result == VK_SUCCESS) { "vkAllocateMemory failed: $result" }
        memoryVar.value!!.rawValue.toLong()
    }

    /** Frees device memory. Buffers and images bound to it must no longer be used. */
    actual fun vkFreeMemory(device: Long, memory: Long) {
        nativeVkFreeMemory(device.toCPointer(), memory.toCPointer<VkDeviceMemory_T>(), null)
    }

    /**
     * Binds a region of device memory to a buffer. This can be done once per buffer, before its
     * first use.
     */
    actual fun vkBindBufferMemory(device: Long, buffer: Long, memory: Long, memoryOffset: Long) {
        val result = nativeVkBindBufferMemory(
            device.toCPointer(),
            buffer.toCPointer<VkBuffer_T>(),
            memory.toCPointer<VkDeviceMemory_T>(),
            memoryOffset.toULong(),
        )
        check(result == VK_SUCCESS) { "vkBindBufferMemory failed: $result" }
    }

    /**
     * Maps [memory], copies [data] into it at [offset], and unmaps it again.
     *
     * The memory must be host-visible, and host-coherent unless the caller flushes it. [data] must
     * not be empty, because mapping a zero-byte range is invalid.
     */
    actual fun writeBufferMemoryFloats(device: Long, memory: Long, offset: Long, data: FloatArray) {
        if (data.isEmpty()) return
        memScoped {
            val dataVar = alloc<kotlinx.cinterop.COpaquePointerVar>()
            val nativeDevice = device.toCPointer<VkDevice_T>()
            val nativeMemory = memory.toCPointer<VkDeviceMemory_T>()
            val byteSize = (data.size * Float.SIZE_BYTES).toULong()
            val result = nativeVkMapMemory(nativeDevice, nativeMemory, offset.toULong(), byteSize, 0u, dataVar.ptr)
            check(result == VK_SUCCESS) { "vkMapMemory failed: $result" }
            data.usePinned { pinned ->
                memcpy(dataVar.value, pinned.addressOf(0), byteSize)
            }
            nativeVkUnmapMemory(nativeDevice, nativeMemory)
        }
    }

    /**
     * Maps [memory], copies [count] floats of [data] from [dataOffset] into it at [offset], and
     * unmaps it again: part of an array written in place, with no copy of the range first.
     */
    actual fun writeBufferMemoryFloats(device: Long, memory: Long, offset: Long, data: FloatArray, dataOffset: Int, count: Int) {
        requireFloatRange(data.size, dataOffset, count)
        // vkMapMemory rejects a zero size, and an empty write has nothing to do anyway.
        if (count == 0) return
        memScoped {
            val dataVar = alloc<kotlinx.cinterop.COpaquePointerVar>()
            val nativeDevice = device.toCPointer<VkDevice_T>()
            val nativeMemory = memory.toCPointer<VkDeviceMemory_T>()
            val byteSize = (count * Float.SIZE_BYTES).toULong()
            val result = nativeVkMapMemory(nativeDevice, nativeMemory, offset.toULong(), byteSize, 0u, dataVar.ptr)
            check(result == VK_SUCCESS) { "vkMapMemory failed: $result" }
            data.usePinned { pinned ->
                memcpy(dataVar.value, pinned.addressOf(dataOffset), byteSize)
            }
            nativeVkUnmapMemory(nativeDevice, nativeMemory)
        }
    }

    /**
     * Same map->memcpy->unmap pattern as [writeBufferMemoryFloats], for raw byte data (e.g. texture
     * pixels) instead of float uniform/vertex data.
     */
    actual fun writeBufferMemoryBytes(device: Long, memory: Long, offset: Long, data: ByteArray) = memScoped {
        val dataVar = alloc<kotlinx.cinterop.COpaquePointerVar>()
        val nativeDevice = device.toCPointer<VkDevice_T>()
        val nativeMemory = memory.toCPointer<VkDeviceMemory_T>()
        val byteSize = data.size.toULong()
        val result = nativeVkMapMemory(nativeDevice, nativeMemory, offset.toULong(), byteSize, 0u, dataVar.ptr)
        check(result == VK_SUCCESS) { "vkMapMemory failed: $result" }
        data.usePinned { pinned ->
            memcpy(dataVar.value, pinned.addressOf(0), byteSize)
        }
        nativeVkUnmapMemory(nativeDevice, nativeMemory)
    }

    /** `bindingCount` is implicit (`buffers.size`); `offsets` must be the same size. */
    actual fun vkCmdBindVertexBuffers(
        commandBuffer: Long,
        firstBinding: Int,
        buffers: LongArray,
        offsets: LongArray,
    ) = memScoped {
        val nativeBuffers = allocArray<CPointerVar<VkBuffer_T>>(buffers.size) { i ->
            value = buffers[i].toCPointer()
        }
        val nativeOffsets = allocArray<kotlinx.cinterop.ULongVar>(offsets.size) { i ->
            value = offsets[i].toULong()
        }
        nativeVkCmdBindVertexBuffers(
            commandBuffer.toCPointer(),
            firstBinding.toUInt(),
            buffers.size.toUInt(),
            nativeBuffers,
            nativeOffsets,
        )
    }

    /**
     * `indexType` uses the plain-`Int` [com.awakekt.awake.vulkan.models.info.VkIndexType] values.
     */
    actual fun vkCmdBindIndexBuffer(commandBuffer: Long, buffer: Long, offset: Long, indexType: Int) {
        nativeVkCmdBindIndexBuffer(
            commandBuffer.toCPointer(),
            buffer.toCPointer<VkBuffer_T>(),
            offset.toULong(),
            indexType.toUInt(),
        )
    }

    /**
     * Single-region copy (`srcOffset`/`dstOffset` both 0) -- the staging-buffer upload pattern (a
     * HOST_VISIBLE staging buffer written via [writeBufferMemoryFloats]/ [writeBufferMemoryBytes],
     * then copied into a DEVICE_LOCAL destination buffer) never needs more than one region, same
     * simplification as [VulkanImages.vkTransitionImageLayout].
     */
    actual fun vkCmdCopyBuffer(commandBuffer: Long, srcBuffer: Long, dstBuffer: Long, size: Long) = memScoped {
        val region = alloc<NativeVkBufferCopy>().apply {
            srcOffset = 0u
            dstOffset = 0u
            this.size = size.toULong()
        }
        nativeVkCmdCopyBuffer(
            commandBuffer.toCPointer(),
            srcBuffer.toCPointer<VkBuffer_T>(),
            dstBuffer.toCPointer<VkBuffer_T>(),
            1u,
            region.ptr,
        )
    }

    /** Records an indexed draw using the bound index and vertex buffers. */
    actual fun vkCmdDrawIndexed(
        commandBuffer: Long,
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    ) {
        nativeVkCmdDrawIndexed(
            commandBuffer.toCPointer(),
            indexCount.toUInt(),
            instanceCount.toUInt(),
            firstIndex.toUInt(),
            vertexOffset,
            firstInstance.toUInt(),
        )
    }

    /**
     * Blocks until all queues on [device] are idle. Used to fully serialize frames so a single (not
     * per-frame-in-flight) uniform buffer can be safely rewritten every frame without racing the
     * GPU's read of the previous frame -- a deliberate simplification; see the MVP-matrix uniform
     * buffer usage in the demo for the full rationale.
     */
    actual fun vkDeviceWaitIdle(device: Long) {
        nativeVkDeviceWaitIdle(device.toCPointer())
    }

    /** Inverse of [writeBufferMemoryBytes] -- map->memcpy-out->unmap, for offscreen
     * render-target CPU readback (`Renderer.readPixels`). */
    actual fun readBufferMemoryBytes(device: Long, memory: Long, offset: Long, size: Int): ByteArray = memScoped {
        val dataVar = alloc<kotlinx.cinterop.COpaquePointerVar>()
        val nativeDevice = device.toCPointer<VkDevice_T>()
        val nativeMemory = memory.toCPointer<VkDeviceMemory_T>()
        val byteSize = size.toULong()
        val result = nativeVkMapMemory(nativeDevice, nativeMemory, offset.toULong(), byteSize, 0u, dataVar.ptr)
        check(result == VK_SUCCESS) { "vkMapMemory failed: $result" }
        val out = ByteArray(size)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), dataVar.value, byteSize)
        }
        nativeVkUnmapMemory(nativeDevice, nativeMemory)
        out
    }

    /** Persistently maps [size] bytes of host-visible [memory] starting at [offset]. */
    actual fun vkMapMemory(device: Long, memory: Long, offset: Long, size: Long): Long = memScoped {
        require(size > 0L || size == -1L) { "vkMapMemory size must be positive or -1 (VK_WHOLE_SIZE), not $size." }
        val dataVar = alloc<kotlinx.cinterop.COpaquePointerVar>()
        val vkSize = if (size == -1L) ULong.MAX_VALUE else size.toULong()
        val result = nativeVkMapMemory(
            device.toCPointer<VkDevice_T>(),
            memory.toCPointer<VkDeviceMemory_T>(),
            offset.toULong(),
            vkSize,
            0u,
            dataVar.ptr,
        )
        check(result == VK_SUCCESS) { "vkMapMemory failed: $result" }
        dataVar.value!!.rawValue.toLong()
    }

    /** Unmaps host-visible [memory] previously mapped with [vkMapMemory]. */
    actual fun vkUnmapMemory(device: Long, memory: Long) {
        nativeVkUnmapMemory(device.toCPointer<VkDevice_T>(), memory.toCPointer<VkDeviceMemory_T>())
    }

    /** Copies [data] into persistently mapped memory at [mappedPtr] + [offset] bytes. */
    actual fun writeMappedMemoryFloats(mappedPtr: Long, offset: Long, data: FloatArray) {
        if (data.isEmpty()) return
        check(mappedPtr != 0L) { "writeMappedMemoryFloats: mapped pointer not initialized" }
        require(offset >= 0L) { "offset must be non-negative: $offset" }
        val dst = (mappedPtr + offset).toCPointer<kotlinx.cinterop.ByteVar>()!!
        data.usePinned { pinned ->
            memcpy(dst, pinned.addressOf(0), (data.size * Float.SIZE_BYTES).toULong())
        }
    }

    /** Copies [count] floats of [data] from [dataOffset] into persistently mapped memory. */
    actual fun writeMappedMemoryFloats(mappedPtr: Long, offset: Long, data: FloatArray, dataOffset: Int, count: Int) {
        requireFloatRange(data.size, dataOffset, count)
        if (count == 0) return
        check(mappedPtr != 0L) { "writeMappedMemoryFloats: mapped pointer not initialized" }
        require(offset >= 0L) { "offset must be non-negative: $offset" }
        val dst = (mappedPtr + offset).toCPointer<kotlinx.cinterop.ByteVar>()!!
        data.usePinned { pinned ->
            memcpy(dst, pinned.addressOf(dataOffset), (count * Float.SIZE_BYTES).toULong())
        }
    }

    /** Copies [count] ints of [data] from [dataOffset] into persistently mapped memory. */
    actual fun writeMappedMemoryInts(mappedPtr: Long, offset: Long, data: IntArray, dataOffset: Int, count: Int) {
        requireArrayRange(data.size, dataOffset, count, "ints")
        if (count == 0) return
        check(mappedPtr != 0L) { "writeMappedMemoryInts: mapped pointer not initialized" }
        require(offset >= 0L) { "offset must be non-negative: $offset" }
        val dst = (mappedPtr + offset).toCPointer<kotlinx.cinterop.ByteVar>()!!
        data.usePinned { pinned ->
            memcpy(dst, pinned.addressOf(dataOffset), (count * Int.SIZE_BYTES).toULong())
        }
    }

    /** Copies [data] into persistently mapped memory at [mappedPtr] + [offset] bytes. */
    actual fun writeMappedMemoryBytes(mappedPtr: Long, offset: Long, data: ByteArray) {
        if (data.isEmpty()) return
        check(mappedPtr != 0L) { "writeMappedMemoryBytes: mapped pointer not initialized" }
        require(offset >= 0L) { "offset must be non-negative: $offset" }
        val dst = (mappedPtr + offset).toCPointer<kotlinx.cinterop.ByteVar>()!!
        data.usePinned { pinned ->
            memcpy(dst, pinned.addressOf(0), data.size.toULong())
        }
    }

    /** Copies [count] bytes of [data] from [dataOffset] into persistently mapped memory. */
    actual fun writeMappedMemoryBytes(mappedPtr: Long, offset: Long, data: ByteArray, dataOffset: Int, count: Int) {
        requireArrayRange(data.size, dataOffset, count, "bytes")
        if (count == 0) return
        check(mappedPtr != 0L) { "writeMappedMemoryBytes: mapped pointer not initialized" }
        require(offset >= 0L) { "offset must be non-negative: $offset" }
        val dst = (mappedPtr + offset).toCPointer<kotlinx.cinterop.ByteVar>()!!
        data.usePinned { pinned ->
            memcpy(dst, pinned.addressOf(dataOffset), count.toULong())
        }
    }

    /** Reads [size] bytes from persistently mapped memory at [mappedPtr] + [offset] bytes. */
    actual fun readMappedMemoryBytes(mappedPtr: Long, offset: Long, size: Int): ByteArray {
        require(size >= 0) { "size must be non-negative: $size" }
        if (size == 0) return ByteArray(0)
        check(mappedPtr != 0L) { "readMappedMemoryBytes: mapped pointer not initialized" }
        require(offset >= 0L) { "offset must be non-negative: $offset" }
        val src = (mappedPtr + offset).toCPointer<kotlinx.cinterop.ByteVar>()!!
        val out = ByteArray(size)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), src, size.toULong())
        }
        return out
    }

    /** Finds a memory type matching [preferredProperties], falling back to [fallbackProperties], or -1. */
    actual fun findPreferredMemoryType(
        physicalDevice: Long,
        typeFilter: Int,
        preferredProperties: Int,
        fallbackProperties: Int,
    ): Int = memScoped {
        val memProps = alloc<platform.MoltenVK.VkPhysicalDeviceMemoryProperties>()
        nativeVkGetPhysicalDeviceMemoryProperties(physicalDevice.toCPointer(), memProps.ptr)
        val count = memProps.memoryTypeCount.toInt()
        var fallback = -1
        for (i in 0 until count) {
            if ((typeFilter and (1 shl i)) == 0) continue
            val flags = memProps.memoryTypes[i].propertyFlags.toInt()
            if ((flags and preferredProperties) == preferredProperties) return@memScoped i
            if (fallback < 0 && (flags and fallbackProperties) == fallbackProperties) fallback = i
        }
        fallback
    }

    /** Finds a `DEVICE_LOCAL | HOST_VISIBLE | HOST_COHERENT` memory type backed by a heap `>= minHeapBytes`, or -1. */
    actual fun findDeviceLocalHostVisibleMemoryType(
        physicalDevice: Long,
        typeFilter: Int,
        minHeapBytes: Long,
    ): Int = memScoped {
        val memProps = alloc<platform.MoltenVK.VkPhysicalDeviceMemoryProperties>()
        nativeVkGetPhysicalDeviceMemoryProperties(physicalDevice.toCPointer(), memProps.ptr)
        val want =
            com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT or
                com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        val count = memProps.memoryTypeCount.toInt()
        for (i in 0 until count) {
            val memType = memProps.memoryTypes[i]
            val matchesType = (typeFilter and (1 shl i)) != 0
            val matchesFlags = (memType.propertyFlags.toInt() and want) == want
            val heapSize = memProps.memoryHeaps[memType.heapIndex.toInt()].size.toLong()
            if (matchesType && matchesFlags && heapSize >= minHeapBytes) return@memScoped i
        }
        -1
    }

    /** Allocates image memory; MoltenVK already backs each Metal texture with its own allocation. */
    actual fun vkAllocateDedicatedImageMemory(
        device: Long,
        allocationSize: Long,
        memoryTypeIndex: Int,
        image: Long,
        deviceApiVersion: Int,
    ): Long = vkAllocateMemory(device, VkMemoryAllocateInfo(allocationSize, memoryTypeIndex))

    /** Records a single-region buffer copy with explicit [srcOffset] and [dstOffset]. */
    @Suppress("LongParameterList")
    actual fun vkCmdCopyBufferRange(
        commandBuffer: Long,
        srcBuffer: Long,
        dstBuffer: Long,
        srcOffset: Long,
        dstOffset: Long,
        size: Long,
    ) = memScoped {
        val copyRegion = alloc<NativeVkBufferCopy>().apply {
            this.srcOffset = srcOffset.toULong()
            this.dstOffset = dstOffset.toULong()
            this.size = size.toULong()
        }
        nativeVkCmdCopyBuffer(
            commandBuffer.toCPointer(),
            srcBuffer.toCPointer<VkBuffer_T>(),
            dstBuffer.toCPointer<VkBuffer_T>(),
            1u,
            copyRegion.ptr,
        )
    }

    /** Scalar fast path for binding a single vertex buffer with zero heap allocations. */
    actual fun vkCmdBindVertexBuffer(commandBuffer: Long, binding: Int, buffer: Long, offset: Long) = memScoped {
        val nativeBuffer = alloc<VkBufferVar>().apply { value = buffer.toCPointer() }
        val nativeOffset = alloc<kotlinx.cinterop.ULongVar>().apply { value = offset.toULong() }
        nativeVkCmdBindVertexBuffers(
            commandBuffer.toCPointer(),
            binding.toUInt(),
            1u,
            nativeBuffer.ptr,
            nativeOffset.ptr,
        )
    }
}
