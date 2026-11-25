/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.commands

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.QueueLock
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/**
 * Batches `Mesh` and `Texture` staging uploads into persistently-mapped staging chunks so
 * gameplay streaming and bulk asset loads eliminate per-asset `vkQueueSubmit` + `vkWaitForFence`
 * CPU-GPU stalls.
 *
 * Supports two modes:
 * - **Frame-deferred mode** (enabled automatically while a presentable `Renderer` drives frames):
 *   copies are recorded inline at the start of the frame's command buffer (`prepareResources`)
 *   with a single `TRANSFER -> GRAPHICS` barrier, and staging chunks are recycled once the frame's
 *   fence completes.
 * - **Explicit batch mode** ([TransferContext.withUploadBatch]): copies accumulate across multiple
 *   asset constructions and flush in a single `runOneTimeCommands` submission on block exit.
 */
@Suppress("TooManyFunctions")
internal class UploadBatcher(private val graphicsDevice: GraphicsDevice) {
    private val lock = QueueLock()
    private val freeChunks = ArrayList<StagingChunk>()
    private val pendingChunks = ArrayList<StagingChunk>()
    private val pendingCopies = ArrayList<PendingUpload>()
    private val inFlightBatches = ArrayList<InFlightBatch>()
    private var activeChunk: StagingChunk? = null
    private var pendingBytes: Long = 0L
    private var framesSubmitted: Long = 0L

    private var batchDepth: Int = 0
    private var deferToFrame: Boolean = false

    /** True while uploads should be queued into staging chunks instead of blocking per asset. */
    val isBatching: Boolean
        get() = lock.withLock { deferToFrame || batchDepth > 0 }

    /** True when at least one staged upload copy is waiting to be recorded into a command buffer. */
    val hasPending: Boolean
        get() = lock.withLock { pendingCopies.isNotEmpty() }

    /** Number of individual asset uploads queued through this batcher. */
    var batchedUploadCount: Long = 0L
        private set

    /** Number of synchronous batch flushes executed via `runOneTimeCommands`. */
    var syncBatchFlushCount: Long = 0L
        private set

    /** Number of frame-inline flushes recorded into a render command buffer with zero extra submits. */
    var inlineFrameFlushCount: Long = 0L
        private set

    /** Total staging chunks allocated from Vulkan over this batcher's lifetime. */
    var stagingChunksAllocated: Long = 0L
        private set

    /** Idle staging chunks currently held in the warm pool, for tests. */
    val freeChunkCount: Int
        get() = lock.withLock { freeChunks.size }

    /** Total staging bytes currently held across idle and in-flight chunks, for tests. */
    val retainedStagingBytes: Long
        get() = lock.withLock {
            var total = 0L
            for (chunk in freeChunks) total += chunk.capacity
            for (batch in inFlightBatches) {
                for (chunk in batch.chunks) total += chunk.capacity
            }
            total
        }

    /** Enables inline frame-start upload flushing while a presentable renderer is active. */
    fun enableFrameDeferred() = lock.withLock {
        deferToFrame = true
    }

    /**
     * Disables inline frame-start flushing (during renderer teardown after `vkDeviceWaitIdle`),
     * discarding any unrecorded copies and recycling their staging chunks.
     */
    fun disableFrameDeferred() {
        val toFree = lock.withLock {
            deferToFrame = false
            pendingCopies.clear()
            pendingBytes = 0L
            val chunks = detachPendingChunksLocked()
            val extra = mutableListOf<StagingChunk>()
            for (batch in inFlightBatches) {
                for (chunk in batch.chunks) {
                    recycleChunkLocked(chunk, extra)
                }
            }
            inFlightBatches.clear()
            for (chunk in chunks) {
                recycleChunkLocked(chunk, extra)
            }
            extra
        }
        destroyChunks(toFree)
    }

    /** Enters an explicit upload batch scope; balanced by [endBatch]. */
    fun beginBatch() = lock.withLock {
        batchDepth += 1
    }

    /**
     * Exits an explicit upload batch scope, returning `true` if this was the outermost batch and
     * synchronous flushing should run (when not in frame-deferred mode).
     */
    fun endBatch(): Boolean = lock.withLock {
        batchDepth = (batchDepth - 1).coerceAtLeast(0)
        batchDepth == 0 && !deferToFrame && pendingCopies.isNotEmpty()
    }

    /**
     * Stages [byteSize] bytes aligned to [alignment] into a persistently-mapped staging chunk,
     * invokes [writeStaging] immediately while caller arrays are still live, and enqueues
     * [recordCopy] for the next flush. Returns a [PendingUpload] handle the caller must cancel if
     * the destination Vulkan resource is destroyed before the batch flushes. If pending bytes reach
     * [MAX_PENDING_BYTES], flushes synchronously via [flushSync] to cap transient staging memory.
     */
    fun enqueue(
        byteSize: Long,
        alignment: Long,
        flushSync: (((Long) -> Unit) -> Unit),
        writeStaging: (mappedPtr: Long, byteOffset: Long) -> Unit,
        recordCopy: (commandBuffer: Long, stagingBuffer: Long, stagingOffset: Long) -> Unit,
    ): PendingUpload {
        require(byteSize > 0L) { "Upload byteSize must be positive, not $byteSize." }
        val align = alignment.coerceAtLeast(MIN_STAGING_ALIGNMENT)
        val (entry, needAutoFlush) = lock.withLock {
            val (chunk, offset) = acquireSliceLocked(byteSize, align)
            writeStaging(chunk.mappedPtr, offset)
            val stagingBuffer = chunk.buffer
            val pending = PendingUpload(this) { cmd -> recordCopy(cmd, stagingBuffer, offset) }
            pendingCopies.add(pending)
            pendingBytes += byteSize
            batchedUploadCount += 1L
            pending to (pendingBytes >= MAX_PENDING_BYTES)
        }
        if (needAutoFlush) {
            flushSynchronously(flushSync)
        }
        return entry
    }

    /** Marks a queued upload cancelled so a subsequent flush skips its destroyed target handle. */
    fun cancel(upload: PendingUpload) = lock.withLock {
        upload.cancelled = true
    }

    /**
     * Records all live pending uploads directly into [commandBuffer] followed by a single
     * `TRANSFER -> GRAPHICS` barrier, returning the staging chunks whose copies were recorded so
     * the caller can recycle them once [commandBuffer]'s fence completes.
     */
    fun recordPendingInto(commandBuffer: Long): List<StagingChunk> {
        val (copies, chunks) = detachLiveBatch(isInline = true) ?: return emptyList()
        for (index in copies.indices) {
            copies[index].record(commandBuffer)
        }
        VulkanCommands.vkCmdTransferToGraphicsBarrier(commandBuffer)
        return chunks
    }

    /**
     * Records pending uploads into a swapchain/presentable frame's [commandBuffer] and schedules
     * their staging chunks for automatic recycling via [frameSubmitted].
     */
    fun recordPendingForFrame(commandBuffer: Long) {
        val chunks = recordPendingInto(commandBuffer)
        if (chunks.isEmpty()) return
        lock.withLock {
            inFlightBatches.add(InFlightBatch(framesSubmitted, chunks))
        }
    }

    /** Flushes all live pending uploads synchronously via [runOneTimeCommands] and recycles their chunks. */
    fun flushSynchronously(runOneTimeCommands: ((Long) -> Unit) -> Unit) {
        val (copies, chunks) = detachLiveBatch(isInline = false) ?: return
        try {
            runOneTimeCommands { cmd ->
                for (index in copies.indices) {
                    copies[index].record(cmd)
                }
                VulkanCommands.vkCmdTransferToGraphicsBarrier(cmd)
            }
        } finally {
            recycleChunks(chunks)
        }
    }

    private fun detachLiveBatch(isInline: Boolean): Pair<List<PendingUpload>, List<StagingChunk>>? {
        val detached = lock.withLock {
            if (pendingCopies.isEmpty()) return@withLock null
            val live = ArrayList<PendingUpload>(pendingCopies.size)
            for (index in pendingCopies.indices) {
                val entry = pendingCopies[index]
                if (!entry.cancelled) live.add(entry)
            }
            pendingCopies.clear()
            pendingBytes = 0L
            val chunks = detachPendingChunksLocked()
            if (live.isEmpty()) {
                val overflow = mutableListOf<StagingChunk>()
                for (chunk in chunks) {
                    recycleChunkLocked(chunk, overflow)
                }
                DetachedBatch(emptyList(), emptyList(), overflow)
            } else {
                if (isInline) inlineFrameFlushCount += 1L else syncBatchFlushCount += 1L
                DetachedBatch(live, chunks, emptyList())
            }
        } ?: return null
        if (detached.overflowToDestroy.isNotEmpty()) {
            destroyChunks(detached.overflowToDestroy)
        }
        return if (detached.copies.isEmpty()) null else detached.copies to detached.chunks
    }

    /** Advances the submitted-frame counter and recycles staging chunks from completed frames. */
    fun frameSubmitted(framesInFlight: Int, awaitOffscreen: () -> Unit) {
        val hasReady = lock.withLock {
            framesSubmitted += 1L
            inFlightBatches.isNotEmpty() && framesSubmitted - inFlightBatches[0].submittedAt > framesInFlight
        }
        if (!hasReady) return
        awaitOffscreen()
        val toFree = lock.withLock {
            val extra = mutableListOf<StagingChunk>()
            val iterator = inFlightBatches.iterator()
            while (iterator.hasNext()) {
                val batch = iterator.next()
                if (framesSubmitted - batch.submittedAt > framesInFlight) {
                    iterator.remove()
                    for (chunk in batch.chunks) {
                        recycleChunkLocked(chunk, extra)
                    }
                }
            }
            extra
        }
        destroyChunks(toFree)
    }

    /** Returns [chunks] to the warm pool (or destroys oversized/excess chunks) once their GPU fence completed. */
    fun recycleChunks(chunks: List<StagingChunk>) {
        if (chunks.isEmpty()) return
        val toFree = lock.withLock {
            val extra = mutableListOf<StagingChunk>()
            for (chunk in chunks) {
                recycleChunkLocked(chunk, extra)
            }
            extra
        }
        destroyChunks(toFree)
    }

    /**
     * Frees every idle and already-completed staging chunk while keeping any unrecorded
     * [pendingCopies] intact. Caller must have waited for device idle first.
     */
    fun trimIdleChunks() {
        val toFree = lock.withLock {
            val collected = ArrayList<StagingChunk>(freeChunks.size + inFlightBatches.size)
            collected.addAll(freeChunks)
            freeChunks.clear()
            for (batch in inFlightBatches) {
                collected.addAll(batch.chunks)
            }
            inFlightBatches.clear()
            collected
        }
        destroyChunks(toFree)
    }

    /** Frees every staging chunk owned by this batcher. Call only when the device is idle. */
    fun destroy() {
        val all = lock.withLock {
            pendingCopies.clear()
            pendingBytes = 0L
            val collected = mutableListOf<StagingChunk>()
            collected.addAll(detachPendingChunksLocked())
            collected.addAll(freeChunks)
            freeChunks.clear()
            for (batch in inFlightBatches) {
                collected.addAll(batch.chunks)
            }
            inFlightBatches.clear()
            collected
        }
        destroyChunks(all)
    }

    private fun acquireSliceLocked(byteSize: Long, alignment: Long): Pair<StagingChunk, Long> {
        val current = activeChunk
        if (current != null) {
            val aligned = alignUp(current.cursor, alignment)
            if (aligned + byteSize <= current.capacity) {
                current.cursor = aligned + byteSize
                return current to aligned
            }
            pendingChunks.add(current)
            activeChunk = null
        }
        val reusableIndex = freeChunks.indexOfFirst { it.capacity >= byteSize }
        val chosen = if (reusableIndex >= 0) {
            freeChunks.removeAt(reusableIndex).also { it.cursor = byteSize }
        } else {
            allocateChunk(maxOf(DEFAULT_CHUNK_BYTES, byteSize)).also {
                it.cursor = byteSize
                stagingChunksAllocated += 1L
            }
        }
        activeChunk = chosen
        return chosen to 0L
    }

    private fun detachPendingChunksLocked(): List<StagingChunk> {
        val result = ArrayList<StagingChunk>(pendingChunks.size + 1)
        result.addAll(pendingChunks)
        pendingChunks.clear()
        activeChunk?.let {
            result.add(it)
            activeChunk = null
        }
        return result
    }

    private fun recycleChunkLocked(chunk: StagingChunk, overflowOut: MutableList<StagingChunk>) {
        chunk.cursor = 0L
        if (chunk.capacity <= MAX_POOLED_CHUNK_BYTES && freeChunks.size < MAX_POOLED_CHUNKS) {
            freeChunks.add(chunk)
        } else {
            overflowOut.add(chunk)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun allocateChunk(capacity: Long): StagingChunk {
        val device = graphicsDevice.device
        val buffer = VulkanBuffers.vkCreateBuffer(
            device,
            VkBufferCreateInfo(
                size = capacity,
                usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            ),
        )
        val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
        val memoryType = VulkanBuffers.findMemoryType(
            graphicsDevice.physicalDevice,
            requirements.memoryTypeBits,
            VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        )
        var memory = 0L
        var mappedPtr = 0L
        try {
            memory = VulkanBuffers.vkAllocateMemory(
                device,
                VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = memoryType),
            )
            VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0L)
            mappedPtr = VulkanBuffers.vkMapMemory(device, memory, 0L, requirements.size)
            return StagingChunk(buffer, memory, mappedPtr, capacity)
        } catch (failure: Throwable) {
            VulkanBuffers.vkDestroyBuffer(device, buffer)
            if (mappedPtr != 0L) VulkanBuffers.vkUnmapMemory(device, memory)
            if (memory != 0L) VulkanBuffers.vkFreeMemory(device, memory)
            throw failure
        }
    }

    private fun destroyChunks(chunks: List<StagingChunk>) {
        val device = graphicsDevice.device
        if (device == 0L) return
        for (chunk in chunks) {
            VulkanBuffers.vkDestroyBuffer(device, chunk.buffer)
            if (chunk.mappedPtr != 0L) VulkanBuffers.vkUnmapMemory(device, chunk.memory)
            VulkanBuffers.vkFreeMemory(device, chunk.memory)
        }
    }

    /** Handle for one queued staging copy; call [cancel] if the destination is destroyed before flush. */
    internal class PendingUpload internal constructor(
        private val batcher: UploadBatcher,
        internal val record: (Long) -> Unit,
    ) {
        internal var cancelled: Boolean = false

        fun cancel() = batcher.cancel(this)
    }

    internal class StagingChunk(
        val buffer: Long,
        val memory: Long,
        val mappedPtr: Long,
        val capacity: Long,
        var cursor: Long = 0L,
    )

    private class DetachedBatch(
        val copies: List<PendingUpload>,
        val chunks: List<StagingChunk>,
        val overflowToDestroy: List<StagingChunk>,
    )

    private class InFlightBatch(
        val submittedAt: Long,
        val chunks: List<StagingChunk>,
    )

    private companion object {
        const val MIN_STAGING_ALIGNMENT: Long = 16L
        const val DEFAULT_CHUNK_BYTES: Long = 1L * 1024L * 1024L
        const val MAX_POOLED_CHUNK_BYTES: Long = 8L * 1024L * 1024L
        const val MAX_POOLED_CHUNKS: Int = 4
        const val MAX_PENDING_BYTES: Long = 32L * 1024L * 1024L

        fun alignUp(value: Long, alignment: Long): Long {
            val remainder = value % alignment
            return if (remainder == 0L) value else value + (alignment - remainder)
        }
    }
}
