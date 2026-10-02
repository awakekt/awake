/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import cnames.structs.VkQueryPool_T
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import platform.MoltenVK.VK_QUERY_RESULT_64_BIT
import platform.MoltenVK.VK_QUERY_TYPE_TIMESTAMP
import platform.MoltenVK.VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO
import platform.MoltenVK.VK_SUCCESS
import platform.MoltenVK.VkQueryPoolVar
import platform.MoltenVK.VkQueryPoolCreateInfo as NativeVkQueryPoolCreateInfo
import platform.MoltenVK.vkCmdResetQueryPool as nativeVkCmdResetQueryPool
import platform.MoltenVK.vkCmdWriteTimestamp as nativeVkCmdWriteTimestamp
import platform.MoltenVK.vkCreateQueryPool as nativeVkCreateQueryPool
import platform.MoltenVK.vkDestroyQueryPool as nativeVkDestroyQueryPool
import platform.MoltenVK.vkGetQueryPoolResults as nativeVkGetQueryPoolResults

@OptIn(ExperimentalForeignApi::class)
actual object VulkanQueries {
    actual fun vkCreateTimestampQueryPool(device: Long, queryCount: Int): Long = memScoped {
        val info = alloc<NativeVkQueryPoolCreateInfo>().apply {
            sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO
            pNext = null
            flags = 0u
            queryType = VK_QUERY_TYPE_TIMESTAMP
            this.queryCount = queryCount.toUInt()
            pipelineStatistics = 0u
        }
        val pool = alloc<VkQueryPoolVar>()
        val result = nativeVkCreateQueryPool(device.toCPointer(), info.ptr, null, pool.ptr)
        check(result == VK_SUCCESS) { "vkCreateQueryPool failed: $result" }
        pool.value!!.rawValue.toLong()
    }

    actual fun vkDestroyQueryPool(device: Long, queryPool: Long) {
        nativeVkDestroyQueryPool(device.toCPointer(), queryPool.toCPointer<VkQueryPool_T>(), null)
    }

    actual fun vkCmdResetQueryPool(commandBuffer: Long, queryPool: Long, firstQuery: Int, queryCount: Int) {
        nativeVkCmdResetQueryPool(
            commandBuffer.toCPointer(),
            queryPool.toCPointer<VkQueryPool_T>(),
            firstQuery.toUInt(),
            queryCount.toUInt(),
        )
    }

    actual fun vkCmdWriteTimestamp(commandBuffer: Long, pipelineStage: Int, queryPool: Long, query: Int) {
        nativeVkCmdWriteTimestamp(
            commandBuffer.toCPointer(),
            pipelineStage.toUInt(),
            queryPool.toCPointer<VkQueryPool_T>(),
            query.toUInt(),
        )
    }

    actual fun timestampTicksBetween(device: Long, queryPool: Long, firstQuery: Int, validBits: Int): Long = memScoped {
        val stamps = allocArray<ULongVar>(2)
        val result = nativeVkGetQueryPoolResults(
            device.toCPointer(),
            queryPool.toCPointer<VkQueryPool_T>(),
            firstQuery.toUInt(),
            2u,
            (2 * ULong.SIZE_BYTES).toULong(),
            stamps,
            ULong.SIZE_BYTES.toULong(),
            VK_QUERY_RESULT_64_BIT,
        )
        if (result != VK_SUCCESS) return@memScoped -1L
        val mask = if (validBits >= ULong.SIZE_BITS) ULong.MAX_VALUE else (1uL shl validBits) - 1uL
        ((stamps[1] and mask) - (stamps[0] and mask) and mask).toLong()
    }
}
