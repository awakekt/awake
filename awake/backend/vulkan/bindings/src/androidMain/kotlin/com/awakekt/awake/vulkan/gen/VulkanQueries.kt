/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.JniNative

/**
 * Android actual of [VulkanQueries]. Each function is a JNI `external` declaration backed by a
 * hand-written native body in the `awake-vulkan` library, which [com.awakekt.awake.vulkan.Vulkan]
 * loads.
 */
actual object VulkanQueries {
    /** A pool of [queryCount] `VK_QUERY_TYPE_TIMESTAMP` queries. */
    @JniNative("awake_vulkan_queries_create_timestamp_pool")
    actual external fun vkCreateTimestampQueryPool(device: Long, queryCount: Int): Long

    /** Destroys a query pool. No pending command buffer may still use it. */
    @JniNative("awake_vulkan_queries_destroy_pool")
    actual external fun vkDestroyQueryPool(device: Long, queryPool: Long)

    /** Recorded outside a render pass, before the queries are written again. */
    @JniNative("awake_vulkan_queries_cmd_reset")
    actual external fun vkCmdResetQueryPool(commandBuffer: Long, queryPool: Long, firstQuery: Int, queryCount: Int)

    /**
     * Records a write of the GPU clock into one query slot once earlier work has passed the given
     * pipeline stage.
     */
    @JniNative("awake_vulkan_queries_cmd_write_timestamp")
    actual external fun vkCmdWriteTimestamp(commandBuffer: Long, pipelineStage: Int, queryPool: Long, query: Int)

    /**
     * GPU ticks from query [firstQuery] to the one after it, each masked to [validBits], or -1
     * while either has not been written. Never waits for the GPU.
     */
    @JniNative("awake_vulkan_queries_ticks_between")
    actual external fun timestampTicksBetween(device: Long, queryPool: Long, firstQuery: Int, validBits: Int): Long
}
