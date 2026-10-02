/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.JniNative

/**
 * Timestamp queries: the GPU writes its own clock into a pool slot when it reaches a point in a
 * command buffer, and the CPU reads the difference once that work has finished. Only what frame
 * timing needs -- one query type, 64-bit results, and a read that never waits.
 */
expect object VulkanQueries {
    /** A pool of [queryCount] `VK_QUERY_TYPE_TIMESTAMP` queries. */
    @JniNative("awake_vulkan_queries_create_timestamp_pool")
    fun vkCreateTimestampQueryPool(device: Long, queryCount: Int): Long

    @JniNative("awake_vulkan_queries_destroy_pool")
    fun vkDestroyQueryPool(device: Long, queryPool: Long)

    /** Recorded outside a render pass, before the queries are written again. */
    @JniNative("awake_vulkan_queries_cmd_reset")
    fun vkCmdResetQueryPool(commandBuffer: Long, queryPool: Long, firstQuery: Int, queryCount: Int)

    @JniNative("awake_vulkan_queries_cmd_write_timestamp")
    fun vkCmdWriteTimestamp(commandBuffer: Long, pipelineStage: Int, queryPool: Long, query: Int)

    /**
     * GPU ticks from query [firstQuery] to the one after it, each masked to [validBits], or -1
     * while either has not been written. Never waits for the GPU.
     */
    @JniNative("awake_vulkan_queries_ticks_between")
    fun timestampTicksBetween(device: Long, queryPool: Long, firstQuery: Int, validBits: Int): Long
}
