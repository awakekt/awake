/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.renderer.GpuFramePart
import com.awakekt.awake.render.renderer.GpuFrameTimeCounter
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanQueries

/**
 * Times presented and offscreen command buffers, summing submissions at the frame boundary.
 *
 * A presented frame writes a timestamp at its start and one after each [GpuFramePart], so the parts
 * read as the ticks between consecutive timestamps and the frame as their sum (#1709). A slot's
 * timestamps are read back when that slot records again, after its fence has been waited, so the read
 * never stalls. Offscreen queries are read after their own fence, or after the presented
 * frame's fence that follows them on the same queue. Pools are reused only after that read.
 */
internal class GpuFrameTimer private constructor(
    private val device: Long,
    private val pool: Long,
    slots: Int,
    private val validBits: Int,
    private val nanosPerTick: Float,
) {
    private val frameTickets = arrayOfNulls<Long>(slots)
    private val frameOffscreenTickets = Array(slots) { emptyList<Long>() }
    private val counter = GpuFrameTimeCounter()
    private val offscreenPools = HashSet<Long>()
    private val freePools = ArrayDeque<Long>()
    private val measurements = HashMap<Long, Long>()
    private val pendingOffscreen = ArrayList<Long>()

    /** The most recent GPU frame time read back, or null before the first one finished. */
    val lastMs: Float? get() = counter.latestMs

    /** The most recent frame's milliseconds by [GpuFramePart], NaN before the first one finished. */
    val lastPartMs = FloatArray(PARTS) { Float.NaN }
    private val partScratch = FloatArray(PARTS)

    /** The next part the frame being recorded marks; frames are recorded one at a time. */
    private var nextPart = 0

    /** Reads the slot's previous frame, then starts timing this one. Outside any render pass. */
    fun begin(commandBuffer: Long, slot: Int) {
        val first = slot * QUERIES_PER_FRAME
        frameTickets[slot]?.let { ticket -> counter.completeSubmission(ticket, readParts(first)) }
        frameOffscreenTickets[slot].forEach { completeOffscreen(it) }
        frameOffscreenTickets[slot] = emptyList()
        frameTickets[slot] = counter.beginSubmission()
        VulkanQueries.vkCmdResetQueryPool(commandBuffer, pool, first, QUERIES_PER_FRAME)
        VulkanQueries.vkCmdWriteTimestamp(commandBuffer, TOP_OF_PIPE, pool, first)
        nextPart = 0
    }

    /**
     * Marks the end of [part] in the frame [begin] started, in recording order. A part the recording
     * skipped is marked here too, so it reads 0 and every timestamp of the frame is written.
     */
    fun mark(commandBuffer: Long, slot: Int, part: GpuFramePart) {
        while (nextPart <= part.ordinal) {
            VulkanQueries.vkCmdWriteTimestamp(commandBuffer, BOTTOM_OF_PIPE, pool, slot * QUERIES_PER_FRAME + nextPart + 1)
            nextPart += 1
        }
    }

    /** Stops timing the frame [begin] started in [commandBuffer]: the end of its [GpuFramePart.Ui]. */
    fun end(commandBuffer: Long, slot: Int) {
        mark(commandBuffer, slot, GpuFramePart.Ui)
        frameOffscreenTickets[slot] = pendingOffscreen.toList()
        pendingOffscreen.clear()
        counter.publish()
    }

    /** Starts a separate offscreen measurement without sharing a presented frame's query slots. */
    fun beginOffscreen(commandBuffer: Long): Long {
        val offscreenPool = if (freePools.isEmpty()) {
            VulkanQueries.vkCreateTimestampQueryPool(device, OFFSCREEN_QUERIES).also { offscreenPools += it }
        } else {
            freePools.removeFirst()
        }
        val ticket = counter.beginSubmission()
        measurements[ticket] = offscreenPool
        VulkanQueries.vkCmdResetQueryPool(commandBuffer, offscreenPool, 0, OFFSCREEN_QUERIES)
        VulkanQueries.vkCmdWriteTimestamp(commandBuffer, TOP_OF_PIPE, offscreenPool, 0)
        return ticket
    }

    fun endOffscreen(commandBuffer: Long, ticket: Long) {
        VulkanQueries.vkCmdWriteTimestamp(commandBuffer, BOTTOM_OF_PIPE, measurements.getValue(ticket), 1)
        pendingOffscreen += ticket
    }

    /** Call only after a fence covering this submission has completed. */
    fun completeOffscreen(ticket: Long): Double? {
        val offscreenPool = measurements.remove(ticket) ?: return null
        val ticks = VulkanQueries.timestampTicksBetween(device, offscreenPool, 0, validBits)
        val duration = milliseconds(ticks)
        counter.completeSubmission(ticket, duration)
        pendingOffscreen.remove(ticket)
        freePools += offscreenPool
        return duration
    }

    /** Reads the parts of the frame whose timestamps start at [first] into [lastPartMs]; their sum, or null. */
    private fun readParts(first: Int): Double? {
        var total = 0.0
        for (part in 0 until PARTS) {
            // As primitives: through [milliseconds]' `Double?` each part boxed, seven a frame.
            val ticks = VulkanQueries.timestampTicksBetween(device, pool, first + part, validBits)
            if (ticks < 0) return null
            val ms = ticks.toDouble() * nanosPerTick / NANOS_PER_MS
            partScratch[part] = ms.toFloat()
            total += ms
        }
        partScratch.copyInto(lastPartMs)
        return total
    }

    private fun milliseconds(ticks: Long): Double? =
        if (ticks >= 0) ticks.toDouble() * nanosPerTick / NANOS_PER_MS else null

    fun destroy() {
        offscreenPools.forEach { VulkanQueries.vkDestroyQueryPool(device, it) }
        VulkanQueries.vkDestroyQueryPool(device, pool)
    }

    companion object {
        /** A timer for [slots] frames in flight, or null when [graphicsDevice]'s queue cannot time work. */
        fun createOrNull(graphicsDevice: GraphicsDevice, slots: Int): GpuFrameTimer? {
            if (graphicsDevice.timestampValidBits <= 0 || graphicsDevice.timestampPeriodNs <= 0f) return null
            val pool = VulkanQueries.vkCreateTimestampQueryPool(graphicsDevice.device, slots * QUERIES_PER_FRAME)
            return GpuFrameTimer(
                device = graphicsDevice.device,
                pool = pool,
                slots = slots,
                validBits = graphicsDevice.timestampValidBits,
                nanosPerTick = graphicsDevice.timestampPeriodNs,
            )
        }

        private val PARTS = GpuFramePart.entries.size
        private val QUERIES_PER_FRAME = PARTS + 1
        private const val OFFSCREEN_QUERIES = 2
        private const val NANOS_PER_MS = 1_000_000f
        private val TOP_OF_PIPE = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value
        private val BOTTOM_OF_PIPE = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value
    }
}
