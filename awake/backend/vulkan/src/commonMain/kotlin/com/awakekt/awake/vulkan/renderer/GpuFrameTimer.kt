/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.renderer.GpuFrameTimeCounter
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanQueries

/**
 * Times presented and offscreen command buffers, summing submissions at the frame boundary.
 *
 * A slot's pair is read back when that slot records again, after its fence has been waited, so
 * the read never stalls. Offscreen queries are read after their own fence, or after the presented
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

    /** Reads the slot's previous frame, then starts timing this one. Outside any render pass. */
    fun begin(commandBuffer: Long, slot: Int) {
        val first = slot * QUERIES_PER_FRAME
        frameTickets[slot]?.let { ticket ->
            val ticks = VulkanQueries.timestampTicksBetween(device, pool, first, validBits)
            counter.completeSubmission(ticket, milliseconds(ticks))
        }
        frameOffscreenTickets[slot].forEach { completeOffscreen(it) }
        frameOffscreenTickets[slot] = emptyList()
        frameTickets[slot] = counter.beginSubmission()
        VulkanQueries.vkCmdResetQueryPool(commandBuffer, pool, first, QUERIES_PER_FRAME)
        VulkanQueries.vkCmdWriteTimestamp(commandBuffer, TOP_OF_PIPE, pool, first)
    }

    /** Stops timing the frame [begin] started in [commandBuffer]. */
    fun end(commandBuffer: Long, slot: Int) {
        VulkanQueries.vkCmdWriteTimestamp(commandBuffer, BOTTOM_OF_PIPE, pool, slot * QUERIES_PER_FRAME + 1)
        frameOffscreenTickets[slot] = pendingOffscreen.toList()
        pendingOffscreen.clear()
        counter.publish()
    }

    /** Starts a separate offscreen measurement without sharing a presented frame's query slots. */
    fun beginOffscreen(commandBuffer: Long): Long {
        val offscreenPool = if (freePools.isEmpty()) {
            VulkanQueries.vkCreateTimestampQueryPool(device, QUERIES_PER_FRAME).also { offscreenPools += it }
        } else {
            freePools.removeFirst()
        }
        val ticket = counter.beginSubmission()
        measurements[ticket] = offscreenPool
        VulkanQueries.vkCmdResetQueryPool(commandBuffer, offscreenPool, 0, QUERIES_PER_FRAME)
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

        private const val QUERIES_PER_FRAME = 2
        private const val NANOS_PER_MS = 1_000_000f
        private val TOP_OF_PIPE = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT.value
        private val BOTTOM_OF_PIPE = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT.value
    }
}
