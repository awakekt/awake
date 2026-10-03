/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanQueries

/**
 * Times each frame's command buffer on the GPU with a pair of timestamps per frame slot.
 *
 * A slot's pair is read back when that slot records again, after its fence has been waited, so
 * the read never stalls and the value is the frame from [slots] frames ago. Work submitted outside
 * the frame's command buffer, such as an offscreen render, is not included.
 */
internal class GpuFrameTimer private constructor(
    private val device: Long,
    private val pool: Long,
    slots: Int,
    private val validBits: Int,
    private val nanosPerTick: Float,
) {
    private val written = BooleanArray(slots)

    /** The most recent GPU frame time read back, or null before the first one finished. */
    var lastMs: Float? = null
        private set

    /** Reads the slot's previous frame, then starts timing this one. Outside any render pass. */
    fun begin(commandBuffer: Long, slot: Int) {
        val first = slot * QUERIES_PER_FRAME
        if (written[slot]) {
            val ticks = VulkanQueries.timestampTicksBetween(device, pool, first, validBits)
            if (ticks >= 0) lastMs = ticks * nanosPerTick / NANOS_PER_MS
        }
        VulkanQueries.vkCmdResetQueryPool(commandBuffer, pool, first, QUERIES_PER_FRAME)
        VulkanQueries.vkCmdWriteTimestamp(commandBuffer, TOP_OF_PIPE, pool, first)
    }

    /** Stops timing the frame [begin] started in [commandBuffer]. */
    fun end(commandBuffer: Long, slot: Int) {
        VulkanQueries.vkCmdWriteTimestamp(commandBuffer, BOTTOM_OF_PIPE, pool, slot * QUERIES_PER_FRAME + 1)
        written[slot] = true
    }

    fun destroy() = VulkanQueries.vkDestroyQueryPool(device, pool)

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
