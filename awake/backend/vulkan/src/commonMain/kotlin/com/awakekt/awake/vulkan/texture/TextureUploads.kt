/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.texture.TextureRegion
import com.awakekt.awake.render.texture.TextureUploadRecorder
import com.awakekt.awake.render.texture.WritableTexture
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo

/** Smallest staging buffer worth keeping for reuse. */
private const val MIN_STAGING_BYTES = 64L * 1024

/**
 * Staging buffers belong to a frame slot until that slot's next fence, then return to a bounded
 * shared pool, so steady streaming allocates nothing while transient upload bursts never pin
 * unbounded staging memory. Every image written in a frame changes layout once: [write] moves it
 * to transfer on first use, and [endFrame] returns all of them to shader reads.
 */
internal class TextureUploads(private val graphicsDevice: GraphicsDevice) : TextureUploadRecorder {
    private class Staging(val buffer: Long, val memory: Long, val mappedPtr: Long, val size: Long)
    private val inUse = HashMap<Int, MutableList<Staging>>()
    private val free = mutableListOf<Staging>()
    private val touched = mutableListOf<Texture>()
    private var frameIndex = 0
    private var commandBuffer = 0L
    private var flushPendingUploads: (Long) -> Unit = {}
    private var wroteSinceLastBeginFrame = false
    private var idleFrames = 0
    private val device get() = graphicsDevice.device

    /** Quiet frames with zero writes and no in-use staging before idle buffers auto-reclaim. */
    var idleReclaimFrames: Int = DEFAULT_IDLE_RECLAIM_FRAMES

    private val isMobile: Boolean
        get() = graphicsDevice.deviceClass == GpuDeviceClass.Mobile

    private val maxFreeBuffers: Int
        get() = if (isMobile) MOBILE_MAX_FREE_BUFFERS else MAX_FREE_BUFFERS

    private val maxFreeBytes: Long
        get() = if (isMobile) MOBILE_MAX_FREE_BYTES else MAX_FREE_BYTES

    fun beginFrame(frameIndex: Int, commandBuffer: Long, flushPendingUploads: (Long) -> Unit = {}) {
        inUse.remove(frameIndex)?.let { completed ->
            free.addAll(completed)
            enforceFreeBounds()
        }
        reclaimIfIdle()
        wroteSinceLastBeginFrame = false
        touched.clear()
        this.frameIndex = frameIndex
        this.commandBuffer = commandBuffer
        this.flushPendingUploads = flushPendingUploads
    }

    override fun write(texture: WritableTexture, region: TextureRegion) {
        region.validate(texture)
        val image = texture as Texture
        require(!image.blockCompressed) { "A block-compressed texture takes no RGBA8 region writes." }
        if (graphicsDevice.activeUploadBatcher?.hasPending == true) {
            flushPendingUploads(commandBuffer)
        }
        wroteSinceLastBeginFrame = true
        idleFrames = 0
        val staging = acquire(region.data.size.toLong())
        inUse.getOrPut(frameIndex) { mutableListOf() } += staging
        VulkanBuffers.writeMappedMemoryBytes(staging.mappedPtr, 0L, region.data, 0, region.data.size)
        // Regions written in one frame are disjoint, so copies into one image share a single transition.
        if (touched.none { it === image }) {
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image.image.handle,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                layerCount = image.layerCount,
            )
            touched += image
        }
        com.awakekt.awake.vulkan.gen.VulkanTextureRegions.copy(commandBuffer, staging.buffer, image.image.handle, region.layer, region.x, region.y, region.width, region.height)
    }

    /** Returns every image written since [beginFrame] to shader reads, before any pass samples it. */
    fun endFrame() {
        for (image in touched) {
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image.image.handle,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                layerCount = image.layerCount,
            )
        }
        touched.clear()
    }

    /** Evicts largest idle staging buffers when [free] exceeds device-class count or byte caps. */
    private fun enforceFreeBounds() {
        val countCap = maxFreeBuffers
        val byteCap = maxFreeBytes
        var totalBytes = 0L
        for (index in free.indices) totalBytes += free[index].size
        while (free.isNotEmpty() && (free.size > countCap || totalBytes > byteCap)) {
            var largestIndex = 0
            for (index in 1 until free.size) {
                if (free[index].size > free[largestIndex].size) largestIndex = index
            }
            val evicted = free.removeAt(largestIndex)
            totalBytes -= evicted.size
            release(evicted)
        }
    }

    /** Reclaims all idle [free] staging buffers once [idleReclaimFrames] quiet frames elapse. */
    private fun reclaimIfIdle() {
        if (wroteSinceLastBeginFrame || inUse.isNotEmpty() || free.isEmpty()) {
            idleFrames = 0
            return
        }
        idleFrames += 1
        if (idleFrames >= idleReclaimFrames) {
            idleFrames = 0
            free.forEach(::release)
            free.clear()
        }
    }

    /** The smallest pooled buffer that fits, or a new one rounded up so later frames can reuse it. */
    private fun acquire(size: Long): Staging {
        var best = -1
        for (index in free.indices) {
            if (free[index].size >= size && (best < 0 || free[index].size < free[best].size)) best = index
        }
        if (best >= 0) return free.removeAt(best)
        var capacity = MIN_STAGING_BYTES
        while (capacity < size) capacity *= 2
        return allocate(capacity)
    }

    @Suppress("TooGenericExceptionCaught") // Release native staging before propagating any allocation failure.
    private fun allocate(size: Long): Staging {
        val buffer = VulkanBuffers.vkCreateBuffer(device, VkBufferCreateInfo(size = size, usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT))
        var memory = 0L
        try {
            val requirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, buffer)
            val type = VulkanBuffers.findMemoryType(
                graphicsDevice.physicalDevice,
                requirements.memoryTypeBits,
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            )
            memory = VulkanBuffers.vkAllocateMemory(device, VkMemoryAllocateInfo(allocationSize = requirements.size, memoryTypeIndex = type))
            VulkanBuffers.vkBindBufferMemory(device, buffer, memory, 0)
            val mappedPtr = VulkanBuffers.vkMapMemory(device, memory, 0L, requirements.size)
            return Staging(buffer, memory, mappedPtr, size)
        } catch (failure: Throwable) {
            VulkanBuffers.vkDestroyBuffer(device, buffer)
            if (memory != 0L) VulkanBuffers.vkFreeMemory(device, memory)
            throw failure
        }
    }

    private fun release(staging: Staging) {
        VulkanBuffers.vkDestroyBuffer(device, staging.buffer)
        VulkanBuffers.vkUnmapMemory(device, staging.memory)
        VulkanBuffers.vkFreeMemory(device, staging.memory)
    }

    /** Number of staging buffers currently pooled across idle and in-use lists, for tests. */
    val pooledBufferCount: Int
        get() = free.size + inUse.values.sumOf { it.size }

    /** Total staging bytes currently pooled across idle and in-use lists, for tests. */
    val pooledBytes: Long
        get() = free.sumOf { it.size } + inUse.values.sumOf { list -> list.sumOf { it.size } }

    /** Frees all idle and completed staging buffers. Caller must have waited for device idle. */
    fun trimIdle() {
        idleFrames = 0
        wroteSinceLastBeginFrame = false
        inUse.values.forEach { it.forEach(::release) }
        inUse.clear()
        free.forEach(::release)
        free.clear()
    }

    fun destroy() {
        trimIdle()
    }

    internal companion object {
        const val MAX_FREE_BUFFERS: Int = 8
        const val MAX_FREE_BYTES: Long = 8L * 1024L * 1024L
        const val MOBILE_MAX_FREE_BUFFERS: Int = 4
        const val MOBILE_MAX_FREE_BYTES: Long = 2L * 1024L * 1024L
        const val DEFAULT_IDLE_RECLAIM_FRAMES: Int = 120
    }
}
