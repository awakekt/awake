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

/** Separates linear buffer slabs from non-linear image slabs so `bufferImageGranularity` hazards cannot occur. */
internal enum class SlabKind {
    Buffer,
    Image,
}

/**
 * Separates small long-lived assets (icons, placeholder textures, small meshes <= 256 KiB) from
 * larger streaming assets (256 KiB..2 MiB) so one tiny resident texture cannot pin an 8 MiB
 * streaming slab after transient world assets unload.
 */
internal enum class SlabSizeClass {
    Small,
    Large,
}

/**
 * One allocation returned by [DeviceMemorySlabAllocator]: either a sub-range inside a pooled
 * `DEVICE_LOCAL` slab or a standalone `VkDeviceMemory` allocation for oversized resources.
 */
internal class DeviceMemoryAllocation internal constructor(
    val memory: Long,
    val offset: Long,
    val size: Long,
    internal val slab: DeviceMemorySlabAllocator.Slab?,
)

/**
 * Thread-safe `DEVICE_LOCAL` block/slab suballocator for static `Mesh` buffers and sampled
 * `Texture` images.
 *
 * Groups small-to-medium allocations (`<= 2 MB`) into size-tiered slabs (`2 MB -> 4 MB` for small
 * `<= 256 KB` assets, `4 MB -> 8 MB` for larger `256 KB..2 MB` assets) per
 * `(memoryTypeIndex, SlabKind, SlabSizeClass)` bucket with exact-alignment free-list carving and
 * coalescing on free. This cuts `vkAllocateMemory` kernel ioctls by 50x-100x during scene/chunk
 * loading, prevents tiny resident assets from pinning large streaming slabs, and keeps Vulkan
 * allocation counts far below the hardware `maxMemoryAllocationCount` (typically 4096).
 */
internal class DeviceMemorySlabAllocator(private val graphicsDevice: GraphicsDevice) {
    private val lock = QueueLock()
    private val buckets = HashMap<BucketKey, MutableList<Slab>>()

    /** Number of backing `vkAllocateMemory` slab allocations created so far. */
    var slabCountCreated: Long = 0L
        private set

    /** Number of sub-allocations served from pooled slabs so far. */
    var subAllocationCount: Long = 0L
        private set

    /** Number of oversized or fallback standalone `vkAllocateMemory` allocations so far. */
    var standaloneAllocationCount: Long = 0L
        private set

    /** Currently live sub-allocations across all slabs, for leak tests. */
    var activeSubAllocations: Int = 0
        private set

    /** Currently retained backing slabs across all buckets, for tests and diagnostics. */
    val liveSlabCount: Int
        get() = lock.withLock { buckets.values.sumOf { it.size } }

    /** Total backing bytes across currently retained slabs, for tests and diagnostics. */
    val liveSlabBytes: Long
        get() = lock.withLock { buckets.values.sumOf { slabs -> slabs.sumOf { it.size } } }

    /**
     * Allocates `DEVICE_LOCAL` memory matching [requirements] for [kind]. Large images (`> 2 MB`)
     * pass [image] to Vulkan 1.1 dedicated allocation when supported.
     */
    fun allocate(
        requirements: VkMemoryRequirements,
        kind: SlabKind,
        image: Long = 0L,
    ): DeviceMemoryAllocation {
        val device = graphicsDevice.device
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            graphicsDevice.physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
        )
        val size = requirements.size.coerceAtLeast(1L)
        val alignment = requirements.alignment.coerceAtLeast(1L)
        if (size > MAX_SUBALLOC_BYTES || alignment > MAX_SUBALLOC_BYTES) {
            return allocateStandalone(device, size, memoryTypeIndex, kind, image)
        }
        val sizeClass = if (size <= SMALL_SUBALLOC_MAX_BYTES && alignment <= SMALL_SUBALLOC_MAX_BYTES) {
            SlabSizeClass.Small
        } else {
            SlabSizeClass.Large
        }
        return lock.withLock {
            val key = BucketKey(memoryTypeIndex, kind, sizeClass)
            val slabs = buckets.getOrPut(key) { mutableListOf() }
            for (index in slabs.indices) {
                val slab = slabs[index]
                val offset = slab.tryAllocate(size, alignment)
                if (offset >= 0L) {
                    subAllocationCount += 1L
                    activeSubAllocations += 1
                    return@withLock DeviceMemoryAllocation(slab.memory, offset, size, slab)
                }
            }
            val slabSize = nextSlabSize(slabs.size, sizeClass)
            val newSlab = tryCreateSlab(device, slabSize, memoryTypeIndex, key)
            if (newSlab == null) {
                return@withLock allocateStandalone(device, size, memoryTypeIndex, kind, image)
            }
            slabs.add(newSlab)
            slabCountCreated += 1L
            val offset = newSlab.tryAllocate(size, alignment)
            check(offset >= 0L) {
                "Fresh $slabSize-byte slab could not fit $size bytes (alignment $alignment)."
            }
            subAllocationCount += 1L
            activeSubAllocations += 1
            DeviceMemoryAllocation(newSlab.memory, offset, size, newSlab)
        }
    }

    /** Returns [allocation] to its slab (coalescing adjacent free space) or frees a standalone block. */
    fun free(allocation: DeviceMemoryAllocation) {
        val device = graphicsDevice.device
        if (device == 0L || allocation.memory == 0L) return
        val slab = allocation.slab
        if (slab == null) {
            VulkanBuffers.vkFreeMemory(device, allocation.memory)
            return
        }
        var memoryToFree = 0L
        lock.withLock {
            slab.free(allocation.offset, allocation.size)
            activeSubAllocations = (activeSubAllocations - 1).coerceAtLeast(0)
            if (slab.activeCount == 0) {
                val bucket = buckets[slab.key]
                // Free every empty slab beyond the single warm first-tier slab per bucket, and
                // never retain an oversized grown slab as the lone empty warm slab.
                if (bucket != null && (bucket.size > 1 || slab.size > firstSlabSize(slab.key.sizeClass))) {
                    bucket.remove(slab)
                    if (bucket.isEmpty()) buckets.remove(slab.key)
                    memoryToFree = slab.memory
                }
            }
        }
        if (memoryToFree != 0L) {
            VulkanBuffers.vkFreeMemory(device, memoryToFree)
        }
    }

    /** Frees every currently empty slab across all buckets, including warm single-slab caches. */
    fun trimEmptySlabs() {
        val device = graphicsDevice.device
        if (device == 0L) return
        val toFree = lock.withLock {
            val memories = mutableListOf<Long>()
            val bucketIterator = buckets.values.iterator()
            while (bucketIterator.hasNext()) {
                val slabs = bucketIterator.next()
                val slabIterator = slabs.iterator()
                while (slabIterator.hasNext()) {
                    val slab = slabIterator.next()
                    if (slab.activeCount == 0) {
                        memories += slab.memory
                        slabIterator.remove()
                    }
                }
                if (slabs.isEmpty()) {
                    bucketIterator.remove()
                }
            }
            memories
        }
        toFree.forEach { VulkanBuffers.vkFreeMemory(device, it) }
    }

    /** Frees every remaining pooled slab. Call only during `GraphicsDevice.destroy()` once idle. */
    fun destroy() {
        val device = graphicsDevice.device
        val toFree = lock.withLock {
            val memories = mutableListOf<Long>()
            buckets.values.forEach { slabs ->
                slabs.forEach { memories += it.memory }
            }
            buckets.clear()
            activeSubAllocations = 0
            memories
        }
        if (device != 0L) {
            toFree.forEach { VulkanBuffers.vkFreeMemory(device, it) }
        }
    }

    private fun allocateStandalone(
        device: Long,
        size: Long,
        memoryTypeIndex: Int,
        kind: SlabKind,
        image: Long,
    ): DeviceMemoryAllocation {
        val memory = if (kind == SlabKind.Image && image != 0L) {
            VulkanBuffers.vkAllocateDedicatedImageMemory(
                device = device,
                allocationSize = size,
                memoryTypeIndex = memoryTypeIndex,
                image = image,
                deviceApiVersion = graphicsDevice.effectiveApiVersion.vkVersion,
            )
        } else {
            VulkanBuffers.vkAllocateMemory(device, VkMemoryAllocateInfo(size, memoryTypeIndex))
        }
        standaloneAllocationCount += 1L
        return DeviceMemoryAllocation(memory, 0L, size, slab = null)
    }

    private fun tryCreateSlab(device: Long, slabSize: Long, memoryTypeIndex: Int, key: BucketKey): Slab? =
        try {
            val memory = VulkanBuffers.vkAllocateMemory(device, VkMemoryAllocateInfo(slabSize, memoryTypeIndex))
            Slab(memory, slabSize, key)
        } catch (error: VkResultException) {
            if (error.result == VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY) null else throw error
        }

    internal data class BucketKey(
        val memoryTypeIndex: Int,
        val kind: SlabKind,
        val sizeClass: SlabSizeClass,
    )

    private class FreeRange(var offset: Long, var size: Long)

    internal class Slab(
        val memory: Long,
        val size: Long,
        internal val key: BucketKey,
    ) {
        private val freeRanges = mutableListOf(FreeRange(0L, size))
        var activeCount: Int = 0
            private set

        fun tryAllocate(allocSize: Long, alignment: Long): Long {
            for (index in freeRanges.indices) {
                val range = freeRanges[index]
                val aligned = alignUp(range.offset, alignment)
                val padding = aligned - range.offset
                val needed = padding + allocSize
                if (needed <= range.size) {
                    carve(index, range, aligned, padding, allocSize)
                    activeCount += 1
                    return aligned
                }
            }
            return -1L
        }

        private fun carve(index: Int, range: FreeRange, aligned: Long, padding: Long, allocSize: Long) {
            val end = aligned + allocSize
            val rangeEnd = range.offset + range.size
            val trailing = rangeEnd - end
            when {
                padding == 0L && trailing == 0L -> freeRanges.removeAt(index)
                padding == 0L -> {
                    range.offset = end
                    range.size = trailing
                }
                trailing == 0L -> {
                    range.size = padding
                }
                else -> {
                    range.size = padding
                    freeRanges.add(index + 1, FreeRange(end, trailing))
                }
            }
        }

        fun free(offset: Long, allocSize: Long) {
            activeCount = (activeCount - 1).coerceAtLeast(0)
            var insertAt = 0
            while (insertAt < freeRanges.size && freeRanges[insertAt].offset < offset) {
                insertAt++
            }
            freeRanges.add(insertAt, FreeRange(offset, allocSize))
            // Coalesce with next range if contiguous.
            if (insertAt + 1 < freeRanges.size) {
                val current = freeRanges[insertAt]
                val next = freeRanges[insertAt + 1]
                if (current.offset + current.size == next.offset) {
                    current.size += next.size
                    freeRanges.removeAt(insertAt + 1)
                }
            }
            // Coalesce with previous range if contiguous.
            if (insertAt > 0) {
                val prev = freeRanges[insertAt - 1]
                val current = freeRanges[insertAt]
                if (prev.offset + prev.size == current.offset) {
                    prev.size += current.size
                    freeRanges.removeAt(insertAt)
                }
            }
        }
    }

    private companion object {
        const val SMALL_SUBALLOC_MAX_BYTES: Long = 256L * 1024L
        const val MAX_SUBALLOC_BYTES: Long = 2L * 1024L * 1024L
        const val SMALL_FIRST_SLAB_BYTES: Long = 2L * 1024L * 1024L
        const val SMALL_MAX_SLAB_BYTES: Long = 4L * 1024L * 1024L
        const val LARGE_FIRST_SLAB_BYTES: Long = 4L * 1024L * 1024L
        const val LARGE_MAX_SLAB_BYTES: Long = 8L * 1024L * 1024L

        fun firstSlabSize(sizeClass: SlabSizeClass): Long = when (sizeClass) {
            SlabSizeClass.Small -> SMALL_FIRST_SLAB_BYTES
            SlabSizeClass.Large -> LARGE_FIRST_SLAB_BYTES
        }

        fun nextSlabSize(existingSlabs: Int, sizeClass: SlabSizeClass): Long = when (sizeClass) {
            SlabSizeClass.Small -> if (existingSlabs == 0) SMALL_FIRST_SLAB_BYTES else SMALL_MAX_SLAB_BYTES
            SlabSizeClass.Large -> if (existingSlabs == 0) LARGE_FIRST_SLAB_BYTES else LARGE_MAX_SLAB_BYTES
        }

        fun alignUp(value: Long, alignment: Long): Long {
            val remainder = value % alignment
            return if (remainder == 0L) value else value + (alignment - remainder)
        }
    }
}
