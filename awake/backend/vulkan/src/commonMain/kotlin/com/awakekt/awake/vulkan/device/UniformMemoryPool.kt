/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.material.materialLayoutBindings
import com.awakekt.awake.vulkan.material.materialPoolSizes
import com.awakekt.awake.vulkan.models.VkMemoryRequirements
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorPoolSize
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutBinding
import com.awakekt.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDescriptorType
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.utils.VkResultException

/**
 * One allocation returned by [HostUniformMemoryPool]: either an aligned sub-range inside a
 * persistently mapped host-visible slab or a standalone `VkDeviceMemory` allocation for oversized
 * uniform blocks.
 */
internal class HostUniformAllocation internal constructor(
    val memory: Long,
    val offset: Long,
    val mappedPtr: Long,
    val size: Long,
    internal val slab: HostUniformMemoryPool.Slab?,
)

/**
 * Thread-safe, persistently mapped `HOST_VISIBLE | HOST_COHERENT` slab suballocator for uniform
 * buffers (`Material` slot blocks and `PerFrameUniformSlots`).
 *
 * Groups small-to-medium uniform allocations (`<= 256 KiB`) into shared persistently mapped slabs
 * (`256 KiB -> 512 KiB`) bucketed by Vulkan memory type with exact-alignment free-list carving and
 * adjacent-span coalescing on free. This collapses hundreds of per-material `vkAllocateMemory` and
 * `vkMapMemory` calls into a handful of shared slabs, keeping mobile drivers well below
 * `maxMemoryAllocationCount` and reducing kernel virtual-mapping pressure.
 */
internal class HostUniformMemoryPool(private val graphicsDevice: GraphicsDevice) {
    private val lock = QueueLock()
    private val buckets = HashMap<Int, MutableList<Slab>>()

    /** Number of backing `vkAllocateMemory` slabs created so far. */
    var slabCountCreated: Long = 0L
        private set

    /** Number of sub-allocations served from pooled slabs so far. */
    var subAllocationCount: Long = 0L
        private set

    /** Number of oversized or fallback standalone allocations created so far. */
    var standaloneAllocationCount: Long = 0L
        private set

    /** Currently live sub-allocations across all slabs, for leak tests. */
    var activeSubAllocations: Int = 0
        private set

    /** Currently retained backing slabs across all memory-type buckets. */
    val liveSlabCount: Int
        get() = lock.withLock { buckets.values.sumOf { it.size } }

    /** Total backing bytes across currently retained host-visible slabs. */
    val liveSlabBytes: Long
        get() = lock.withLock { buckets.values.sumOf { slabs -> slabs.sumOf { it.size } } }

    /**
     * Allocates persistently mapped `HOST_VISIBLE | HOST_COHERENT` memory matching [requirements],
     * preferring `DEVICE_LOCAL | HOST_VISIBLE` (ReBAR/UMA) heaps when available.
     */
    fun allocate(
        requirements: VkMemoryRequirements,
        minRebarHeapBytes: Long = SMALL_UBO_REBAR_MIN_HEAP_BYTES,
    ): HostUniformAllocation {
        val device = graphicsDevice.device
        val size = requirements.size.coerceAtLeast(1L)
        val alignment = requirements.alignment.coerceAtLeast(1L)
        val fastType = VulkanBuffers.findDeviceLocalHostVisibleMemoryType(
            graphicsDevice.physicalDevice,
            requirements.memoryTypeBits,
            minRebarHeapBytes,
        )
        val fallbackType = VulkanBuffers.findMemoryType(
            graphicsDevice.physicalDevice,
            requirements.memoryTypeBits,
            HOST_VISIBLE_COHERENT,
        )
        if (size > MAX_SUBALLOC_BYTES || alignment > MAX_SUBALLOC_BYTES) {
            return allocateStandalone(device, size, fastType, fallbackType)
        }
        return lock.withLock {
            if (fastType >= 0) {
                tryAllocateFromBucket(device, fastType, size, alignment)?.let { return@withLock it }
            }
            if (fallbackType != fastType) {
                tryAllocateFromBucket(device, fallbackType, size, alignment)?.let { return@withLock it }
            }
            allocateStandalone(device, size, fastType, fallbackType)
        }
    }

    /** Returns [allocation] to its slab (coalescing adjacent free spans) or frees a standalone block. */
    fun free(allocation: HostUniformAllocation) {
        val device = graphicsDevice.device
        if (device == 0L || allocation.memory == 0L) return
        val slab = allocation.slab
        if (slab == null) {
            VulkanBuffers.vkUnmapMemory(device, allocation.memory)
            VulkanBuffers.vkFreeMemory(device, allocation.memory)
            return
        }
        var memoryToFree = 0L
        lock.withLock {
            slab.free(allocation.offset, allocation.size)
            activeSubAllocations = (activeSubAllocations - 1).coerceAtLeast(0)
            if (slab.activeCount == 0) {
                val bucket = buckets[slab.memoryTypeIndex]
                // Release every empty slab beyond a single warm first-tier slab per bucket.
                if (bucket != null && (bucket.size > 1 || slab.size > FIRST_SLAB_BYTES)) {
                    bucket.remove(slab)
                    if (bucket.isEmpty()) buckets.remove(slab.memoryTypeIndex)
                    memoryToFree = slab.memory
                }
            }
        }
        if (memoryToFree != 0L) {
            VulkanBuffers.vkUnmapMemory(device, memoryToFree)
            VulkanBuffers.vkFreeMemory(device, memoryToFree)
        }
    }

    /** Unmaps and frees every currently empty slab across all buckets, including warm caches. */
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
                if (slabs.isEmpty()) bucketIterator.remove()
            }
            memories
        }
        toFree.forEach { memory ->
            VulkanBuffers.vkUnmapMemory(device, memory)
            VulkanBuffers.vkFreeMemory(device, memory)
        }
    }

    /** Unmaps and frees every remaining pooled slab. Call only during `GraphicsDevice.destroy()` once idle. */
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
            toFree.forEach { memory ->
                VulkanBuffers.vkUnmapMemory(device, memory)
                VulkanBuffers.vkFreeMemory(device, memory)
            }
        }
    }

    private fun tryAllocateFromBucket(
        device: Long,
        memoryTypeIndex: Int,
        size: Long,
        alignment: Long,
    ): HostUniformAllocation? {
        val slabs = buckets.getOrPut(memoryTypeIndex) { mutableListOf() }
        return allocateFromExistingSlabs(slabs, size, alignment)
            ?: allocateFromNewSlab(device, slabs, memoryTypeIndex, size, alignment)
    }

    private fun allocateFromNewSlab(
        device: Long,
        slabs: MutableList<Slab>,
        memoryTypeIndex: Int,
        size: Long,
        alignment: Long,
    ): HostUniformAllocation? {
        val slabSize = if (slabs.isEmpty()) FIRST_SLAB_BYTES else GROWTH_SLAB_BYTES
        val newSlab = tryCreateSlab(device, slabSize, memoryTypeIndex) ?: return null
        slabs.add(newSlab)
        slabCountCreated += 1L
        val offset = newSlab.tryAllocate(size, alignment)
        check(offset >= 0L) {
            "Fresh $slabSize-byte uniform slab could not fit $size bytes (alignment $alignment)."
        }
        subAllocationCount += 1L
        activeSubAllocations += 1
        return HostUniformAllocation(newSlab.memory, offset, newSlab.mappedPtr + offset, size, newSlab)
    }

    private fun allocateFromExistingSlabs(
        slabs: List<Slab>,
        size: Long,
        alignment: Long,
    ): HostUniformAllocation? {
        for (index in slabs.indices) {
            val slab = slabs[index]
            val offset = slab.tryAllocate(size, alignment)
            if (offset >= 0L) {
                subAllocationCount += 1L
                activeSubAllocations += 1
                return HostUniformAllocation(slab.memory, offset, slab.mappedPtr + offset, size, slab)
            }
        }
        return null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun tryCreateSlab(device: Long, slabSize: Long, memoryTypeIndex: Int): Slab? {
        val memory = try {
            VulkanBuffers.vkAllocateMemory(device, VkMemoryAllocateInfo(slabSize, memoryTypeIndex))
        } catch (error: VkResultException) {
            if (error.result == VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY) return null else throw error
        }
        return try {
            val mappedPtr = VulkanBuffers.vkMapMemory(device, memory, 0L, slabSize)
            Slab(memory, mappedPtr, slabSize, memoryTypeIndex)
        } catch (failure: Throwable) {
            VulkanBuffers.vkFreeMemory(device, memory)
            throw failure
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun allocateStandalone(
        device: Long,
        size: Long,
        fastType: Int,
        fallbackType: Int,
    ): HostUniformAllocation {
        val memory = allocateStandaloneMemory(device, size, fastType, fallbackType)
        val mappedPtr = try {
            VulkanBuffers.vkMapMemory(device, memory, 0L, size)
        } catch (failure: Throwable) {
            VulkanBuffers.vkFreeMemory(device, memory)
            throw failure
        }
        standaloneAllocationCount += 1L
        return HostUniformAllocation(memory, 0L, mappedPtr, size, slab = null)
    }

    private fun allocateStandaloneMemory(
        device: Long,
        size: Long,
        fastType: Int,
        fallbackType: Int,
    ): Long {
        if (fastType >= 0) {
            try {
                return VulkanBuffers.vkAllocateMemory(device, VkMemoryAllocateInfo(size, fastType))
            } catch (error: VkResultException) {
                if (error.result != VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY) throw error
            }
        }
        return VulkanBuffers.vkAllocateMemory(device, VkMemoryAllocateInfo(size, fallbackType))
    }

    private class FreeRange(var offset: Long, var size: Long)

    internal class Slab(
        val memory: Long,
        val mappedPtr: Long,
        val size: Long,
        val memoryTypeIndex: Int,
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
                trailing == 0L -> range.size = padding
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
            if (insertAt + 1 < freeRanges.size) {
                val current = freeRanges[insertAt]
                val next = freeRanges[insertAt + 1]
                if (current.offset + current.size == next.offset) {
                    current.size += next.size
                    freeRanges.removeAt(insertAt + 1)
                }
            }
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
        const val HOST_VISIBLE_COHERENT =
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        const val MAX_SUBALLOC_BYTES: Long = 256L * 1024L
        const val FIRST_SLAB_BYTES: Long = 256L * 1024L
        const val GROWTH_SLAB_BYTES: Long = 512L * 1024L

        fun alignUp(value: Long, alignment: Long): Long {
            val remainder = value % alignment
            return if (remainder == 0L) value else value + (alignment - remainder)
        }
    }
}

/** One shared `VkDescriptorPool` chunk managed by [SharedDescriptorPoolAllocator]. */
internal class DescriptorPoolChunk internal constructor(
    val pool: Long,
    val capacity: Int,
    internal val key: SharedDescriptorPoolAllocator.PoolKey,
) {
    var reservedSets: Int = 0
    var liveBlocks: Int = 0
    val remainingCapacity: Int get() = capacity - reservedSets
}

/**
 * Ref-counted chunked `VkDescriptorPool` allocator shared across `Material` slot blocks and
 * `PerFrameUniformSlots`.
 *
 * Rather than creating one `VkDescriptorPool` per material or per frame slot, blocks reserve their
 * set count from 128-set chunks keyed by descriptor shape. Each chunk tracks active blocks and is
 * destroyed via `vkDestroyDescriptorPool` once every block using it has been released (and it is
 * either retired or full, or during [trimEmptyPools]).
 */
internal class SharedDescriptorPoolAllocator(private val graphicsDevice: GraphicsDevice) {
    internal sealed interface PoolKey {
        data class Grouped(val bindings: GroupBindings) : PoolKey
        data object SingleUniform : PoolKey
    }

    private class Bucket {
        var activeChunk: DescriptorPoolChunk? = null
        val retiredChunks = mutableListOf<DescriptorPoolChunk>()
    }

    private val lock = QueueLock()
    private val buckets = HashMap<PoolKey, Bucket>()

    /** Total `VkDescriptorPool` chunks created so far. */
    var poolsCreated: Long = 0L
        private set

    /** Currently live `VkDescriptorPool` chunks across all descriptor shapes. */
    val livePoolCount: Int
        get() = lock.withLock {
            buckets.values.sumOf { (if (it.activeChunk != null) 1 else 0) + it.retiredChunks.size }
        }

    /** Currently live block reservations across all chunks, for leak tests. */
    var activeBlockCount: Int = 0
        private set

    /** Reserves [slotCount] descriptor sets shaped by [bindings] from a shared chunk. */
    fun reserveBlock(bindings: GroupBindings, slotCount: Int): DescriptorPoolChunk =
        reserve(PoolKey.Grouped(bindings), slotCount)

    /** Reserves [slotCount] uniform-only descriptor sets (binding 0) from a shared chunk. */
    fun reserveSingleUniformBlock(slotCount: Int): DescriptorPoolChunk =
        reserve(PoolKey.SingleUniform, slotCount)

    private fun reserve(key: PoolKey, slotCount: Int): DescriptorPoolChunk {
        require(slotCount > 0) { "Descriptor pool reservation must request at least 1 set, got $slotCount." }
        var retiredPoolToDestroy = 0L
        val chunk = lock.withLock {
            val bucket = buckets.getOrPut(key) { Bucket() }
            val current = bucket.activeChunk
            if (current != null && current.remainingCapacity >= slotCount) {
                current.reservedSets += slotCount
                current.liveBlocks += 1
                activeBlockCount += 1
                return@withLock current
            }
            if (current != null) {
                if (current.liveBlocks == 0) {
                    retiredPoolToDestroy = current.pool
                } else {
                    bucket.retiredChunks += current
                }
                bucket.activeChunk = null
            }
            val capacity = maxOf(SETS_PER_CHUNK, slotCount)
            val created = DescriptorPoolChunk(
                pool = createPool(key, capacity),
                capacity = capacity,
                key = key,
            ).apply {
                reservedSets = slotCount
                liveBlocks = 1
            }
            bucket.activeChunk = created
            poolsCreated += 1L
            activeBlockCount += 1
            created
        }
        if (retiredPoolToDestroy != 0L && graphicsDevice.device != 0L) {
            VulkanDescriptors.vkDestroyDescriptorPool(graphicsDevice.device, retiredPoolToDestroy)
        }
        return chunk
    }

    /** Releases one block reservation on [chunk], destroying the pool once drained and retired or full. */
    fun releaseBlock(chunk: DescriptorPoolChunk) {
        val device = graphicsDevice.device
        var poolToDestroy = 0L
        lock.withLock {
            chunk.liveBlocks = (chunk.liveBlocks - 1).coerceAtLeast(0)
            activeBlockCount = (activeBlockCount - 1).coerceAtLeast(0)
            if (chunk.liveBlocks == 0) {
                val bucket = buckets[chunk.key]
                if (bucket != null) {
                    if (bucket.retiredChunks.remove(chunk)) {
                        poolToDestroy = chunk.pool
                    } else if (bucket.activeChunk === chunk && chunk.remainingCapacity == 0) {
                        bucket.activeChunk = null
                        poolToDestroy = chunk.pool
                    }
                    if (bucket.activeChunk == null && bucket.retiredChunks.isEmpty()) {
                        buckets.remove(chunk.key)
                    }
                }
            }
        }
        if (poolToDestroy != 0L && device != 0L) {
            VulkanDescriptors.vkDestroyDescriptorPool(device, poolToDestroy)
        }
    }

    /** Destroys every chunk with zero live blocks, including partially used active chunks. */
    fun trimEmptyPools() {
        val device = graphicsDevice.device
        if (device == 0L) return
        val poolsToDestroy = lock.withLock {
            val pools = mutableListOf<Long>()
            val iterator = buckets.values.iterator()
            while (iterator.hasNext()) {
                val bucket = iterator.next()
                val retiredIterator = bucket.retiredChunks.iterator()
                while (retiredIterator.hasNext()) {
                    val chunk = retiredIterator.next()
                    if (chunk.liveBlocks == 0) {
                        pools += chunk.pool
                        retiredIterator.remove()
                    }
                }
                val active = bucket.activeChunk
                if (active != null && active.liveBlocks == 0) {
                    pools += active.pool
                    bucket.activeChunk = null
                }
                if (bucket.activeChunk == null && bucket.retiredChunks.isEmpty()) {
                    iterator.remove()
                }
            }
            pools
        }
        poolsToDestroy.forEach { VulkanDescriptors.vkDestroyDescriptorPool(device, it) }
    }

    /** Destroys every remaining chunk. Call only during `GraphicsDevice.destroy()` once idle. */
    fun destroy() {
        val device = graphicsDevice.device
        val poolsToDestroy = lock.withLock {
            val pools = mutableListOf<Long>()
            buckets.values.forEach { bucket ->
                bucket.activeChunk?.let { pools += it.pool }
                bucket.retiredChunks.forEach { pools += it.pool }
            }
            buckets.clear()
            activeBlockCount = 0
            pools
        }
        if (device != 0L) {
            poolsToDestroy.forEach { VulkanDescriptors.vkDestroyDescriptorPool(device, it) }
        }
    }

    private fun createPool(key: PoolKey, maxSets: Int): Long {
        val perSetSizes = when (key) {
            is PoolKey.Grouped -> materialPoolSizes(key.bindings)
            PoolKey.SingleUniform -> listOf(
                VkDescriptorPoolSize(type = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, descriptorCount = 1),
            )
        }
        return VulkanDescriptors.vkCreateDescriptorPool(
            graphicsDevice.device,
            VkDescriptorPoolCreateInfo(
                maxSets = maxSets,
                pPoolSizes = perSetSizes
                    .map { VkDescriptorPoolSize(type = it.type, descriptorCount = it.descriptorCount * maxSets) }
                    .toTypedArray(),
            ),
        )
    }

    private companion object {
        const val SETS_PER_CHUNK = 128
    }
}

/**
 * Device-wide cache of immutable `VkDescriptorSetLayout` handles keyed by [GroupBindings] or
 * uniform-only shader stage flags.
 *
 * Avoids creating and destroying one identical `VkDescriptorSetLayout` per `Material` or
 * `PerFrameUniformSlots`; cached handles live for the lifetime of the `GraphicsDevice` and are
 * destroyed together in [destroy].
 */
internal class SharedDescriptorSetLayoutCache(private val graphicsDevice: GraphicsDevice) {
    private val lock = QueueLock()
    private val byBindings = HashMap<GroupBindings, DescriptorSetLayoutHandle>()
    private val bySingleUniformStages = HashMap<Int, DescriptorSetLayoutHandle>()

    /** Number of cached `VkDescriptorSetLayout` handles currently held by this device. */
    val cachedLayoutCount: Int
        get() = lock.withLock { byBindings.size + bySingleUniformStages.size }

    /** Returns the shared layout for [bindings], creating it once on first request. */
    fun layoutFor(bindings: GroupBindings): DescriptorSetLayoutHandle = lock.withLock {
        byBindings.getOrPut(bindings) {
            DescriptorSetLayoutHandle(
                VulkanDescriptors.vkCreateDescriptorSetLayout(
                    graphicsDevice.device,
                    VkDescriptorSetLayoutCreateInfo(
                        pBindings = materialLayoutBindings(bindings).toTypedArray(),
                    ),
                ),
            )
        }
    }

    /** Returns the shared binding-0 uniform-buffer layout for [stageFlags], creating it once. */
    fun singleUniformLayoutFor(stageFlags: Int): DescriptorSetLayoutHandle = lock.withLock {
        bySingleUniformStages.getOrPut(stageFlags) {
            DescriptorSetLayoutHandle(
                VulkanDescriptors.vkCreateDescriptorSetLayout(
                    graphicsDevice.device,
                    VkDescriptorSetLayoutCreateInfo(
                        pBindings = arrayOf(
                            VkDescriptorSetLayoutBinding(
                                binding = 0,
                                descriptorType = VkDescriptorType.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                                stageFlags = stageFlags,
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    /** Destroys every cached descriptor set layout. Call only during `GraphicsDevice.destroy()` once idle. */
    fun destroy() {
        val device = graphicsDevice.device
        val layouts = lock.withLock {
            val handles = mutableListOf<Long>()
            byBindings.values.forEach { handles += it.handle }
            bySingleUniformStages.values.forEach { handles += it.handle }
            byBindings.clear()
            bySingleUniformStages.clear()
            handles
        }
        if (device != 0L) {
            layouts.forEach { VulkanDescriptors.vkDestroyDescriptorSetLayout(device, it) }
        }
    }
}
