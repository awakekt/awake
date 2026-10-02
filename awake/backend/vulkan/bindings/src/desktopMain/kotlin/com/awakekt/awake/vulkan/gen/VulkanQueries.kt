/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import com.awakekt.awake.vulkan.JniNative

actual object VulkanQueries {
    @JniNative("awake_vulkan_queries_create_timestamp_pool")
    actual external fun vkCreateTimestampQueryPool(device: Long, queryCount: Int): Long

    @JniNative("awake_vulkan_queries_destroy_pool")
    actual external fun vkDestroyQueryPool(device: Long, queryPool: Long)

    @JniNative("awake_vulkan_queries_cmd_reset")
    actual external fun vkCmdResetQueryPool(commandBuffer: Long, queryPool: Long, firstQuery: Int, queryCount: Int)

    @JniNative("awake_vulkan_queries_cmd_write_timestamp")
    actual external fun vkCmdWriteTimestamp(commandBuffer: Long, pipelineStage: Int, queryPool: Long, query: Int)

    @JniNative("awake_vulkan_queries_ticks_between")
    actual external fun timestampTicksBetween(device: Long, queryPool: Long, firstQuery: Int, validBits: Int): Long
}
