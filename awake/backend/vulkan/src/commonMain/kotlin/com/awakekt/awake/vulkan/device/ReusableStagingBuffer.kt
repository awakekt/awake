/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/**
 * A persistently mapped `TRANSFER_SRC` staging buffer checked out from [ReusableStagingBuffer].
 */
internal class StagingAllocation(
    val buffer: Long,
    val memory: Long,
    val mappedPtr: Long,
    val capacityBytes: Long,
)

/**
 * Thread-safe reusable persistently-mapped staging buffer pool owned by [GraphicsDevice].
 *
 * Eliminates per-mesh and per-texture `vkCreateBuffer`/`vkAllocateMemory`/`vkMapMemory`/
 * `vkUnmapMemory`/`vkDestroyBuffer`/`vkFreeMemory` churn on uploads up to `maxRetainedBytes`,
 * while releasing oversized one-off uploads immediately so peak host memory does not stay inflated.
 * Idle cached buffers also reclaim automatically after `idleReclaimFrames` quiet frames.
 *
 * The internal lock is never held across user upload callbacks, avoiding any lock-order inversion
 * with `TransferContext.uploadLock` or `GraphicsDevice.queueLock`.
 */
internal class ReusableStagingBuffer(private val graphicsDevice: GraphicsDevice) {
    private val lock = QueueLock()
    private var cached: StagingAllocation? = null
    private var destroyed = false
    private var idleFrames = 0

    /** Quiet presented frames without staging checkouts before an idle cached buffer is freed. */
    var idleReclaimFrames: Int = DEFAULT_IDLE_RECLAIM_FRAMES

    private val maxRetainedBytes: Long
        get() = if (graphicsDevice.deviceClass == GpuDeviceClass.Mobile) MOBILE_MAX_RETAINED_BYTES else MAX_RETAINED_BYTES

    /** Total staging buffers allocated across this device's lifetime, for tests and diagnostics. */
    var allocations: Long = 0L
        private set

    /** Total checkouts served from the cached staging buffer without reallocating, for tests. */
    var reuses: Long = 0L
        private set

    /** Bytes currently retained by the cached staging buffer, or 0 when empty, for tests. */
    val cachedBytes: Long
        get() = lock.withLock { cached?.capacityBytes ?: 0L }

    /**
     * Checks out a persistently mapped staging buffer of at least [requiredBytes] bytes, runs
     * [block] against it, and returns the buffer to the pool (or frees it if oversized).
     */
    inline fun <T> withStaging(requiredBytes: Long, block: (StagingAllocation) -> T): T {
        val allocation = acquire(requiredBytes.coerceAtLeast(MIN_STAGING_BYTES))
        return try {
            block(allocation)
        } finally {
            release(allocation)
        }
    }

    internal fun acquire(requiredBytes: Long): StagingAllocation {
        val taken = lock.withLock {
            idleFrames = 0
            val existing = cached
            if (existing != null) {
                cached = null
                existing
            } else {
                null
            }
        }
        if (taken != null) {
            if (taken.capacityBytes >= requiredBytes) {
                lock.withLock { reuses++ }
                return taken
            }
            freeAllocation(taken)
        }
        val cap = maxRetainedBytes
        val targetBytes = if (requiredBytes <= cap) {
            nextStagingCapacity(requiredBytes, cap)
        } else {
            requiredBytes
        }
        return allocateFresh(targetBytes)
    }

    internal fun release(allocation: StagingAllocation) {
        if (allocation.capacityBytes > maxRetainedBytes) {
            freeAllocation(allocation)
            return
        }
        val displaced = lock.withLock {
            idleFrames = 0
            if (destroyed) {
                allocation
            } else {
                val existing = cached
                if (existing == null || allocation.capacityBytes > existing.capacityBytes) {
                    cached = allocation
                    existing
                } else {
                    allocation
                }
            }
        }
        if (displaced != null) {
            freeAllocation(displaced)
        }
    }

    /** Reclaims the cached staging buffer automatically once [idleReclaimFrames] quiet frames pass. */
    fun frameSubmitted() {
        val toFree = lock.withLock {
            val current = cached
            if (current == null) {
                idleFrames = 0
                null
            } else {
                idleFrames += 1
                if (idleFrames >= idleReclaimFrames) {
                    idleFrames = 0
                    cached = null
                    current
                } else {
                    null
                }
            }
        }
        if (toFree != null) {
            freeAllocation(toFree)
        }
    }

    /** Frees any cached staging buffer without disabling reuse for future uploads. */
    fun trim() {
        val toFree = lock.withLock {
            idleFrames = 0
            val current = cached
            cached = null
            current
        }
        if (toFree != null) {
            freeAllocation(toFree)
        }
    }

    /** Destroys any cached staging buffer. Call before `vkDestroyDevice` with the device idle. */
    fun destroy() {
        val toFree = lock.withLock {
            destroyed = true
            idleFrames = 0
            val current = cached
            cached = null
            current
        }
        if (toFree != null) {
            freeAllocation(toFree)
        }
    }

    private fun allocateFresh(byteSize: Long): StagingAllocation {
        val device = graphicsDevice.device
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(size = byteSize, usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memoryTypeIndex = VulkanBuffers.findMemoryType(
            graphicsDevice.physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        val memory = VulkanBuffers.vkAllocateMemory(
            device,
            VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryTypeIndex),
        )
        VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0L)
        val mappedPtr = VulkanBuffers.vkMapMemory(device, memory, 0L, requirements.size)
        lock.withLock { allocations++ }
        return StagingAllocation(buffer, memory, mappedPtr, byteSize)
    }

    private fun freeAllocation(allocation: StagingAllocation) {
        val device = graphicsDevice.device
        if (device == 0L) return
        VulkanBuffers.vkDestroyBuffer(device, allocation.buffer)
        VulkanBuffers.vkUnmapMemory(device, allocation.memory)
        VulkanBuffers.vkFreeMemory(device, allocation.memory)
    }

    internal companion object {
        const val MIN_STAGING_BYTES: Long = 64L * 1024L
        const val MAX_RETAINED_BYTES: Long = 16L * 1024L * 1024L
        const val MOBILE_MAX_RETAINED_BYTES: Long = 2L * 1024L * 1024L
        const val DEFAULT_IDLE_RECLAIM_FRAMES: Int = 120

        private fun nextStagingCapacity(requiredBytes: Long, maxRetainedBytes: Long): Long {
            var cap = MIN_STAGING_BYTES
            while (cap < requiredBytes && cap < maxRetainedBytes) {
                cap = minOf(cap * 2L, maxRetainedBytes)
            }
            return maxOf(cap, requiredBytes)
        }
    }
}
