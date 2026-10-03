/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <cstdint>

#include <jni.h>
#include <vulkan/vulkan.h>

#include "jni-utils.h"

extern "C" jlong awake_vulkan_queries_create_timestamp_pool(JNIEnv* env, jlong device, jint queryCount) {
    VkQueryPoolCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
    info.queryType = VK_QUERY_TYPE_TIMESTAMP;
    info.queryCount = static_cast<uint32_t>(queryCount);
    VkQueryPool pool = VK_NULL_HANDLE;
    VkResult result = vkCreateQueryPool(reinterpret_cast<VkDevice>(device), &info, nullptr, &pool);
    if (result != VK_SUCCESS) {
        throw_illegal_state(env, "vkCreateQueryPool failed");
        return 0;
    }
    return reinterpret_cast<jlong>(pool);
}

extern "C" void awake_vulkan_queries_destroy_pool(JNIEnv*, jlong device, jlong queryPool) {
    vkDestroyQueryPool(reinterpret_cast<VkDevice>(device), reinterpret_cast<VkQueryPool>(queryPool), nullptr);
}

extern "C" void awake_vulkan_queries_cmd_reset(
        JNIEnv*,
        jlong commandBuffer,
        jlong queryPool,
        jint firstQuery,
        jint queryCount) {
    vkCmdResetQueryPool(
            reinterpret_cast<VkCommandBuffer>(commandBuffer),
            reinterpret_cast<VkQueryPool>(queryPool),
            static_cast<uint32_t>(firstQuery),
            static_cast<uint32_t>(queryCount));
}

extern "C" void awake_vulkan_queries_cmd_write_timestamp(
        JNIEnv*,
        jlong commandBuffer,
        jint pipelineStage,
        jlong queryPool,
        jint query) {
    vkCmdWriteTimestamp(
            reinterpret_cast<VkCommandBuffer>(commandBuffer),
            static_cast<VkPipelineStageFlagBits>(pipelineStage),
            reinterpret_cast<VkQueryPool>(queryPool),
            static_cast<uint32_t>(query));
}

// Two consecutive 64-bit timestamps without VK_QUERY_RESULT_WAIT_BIT: VK_NOT_READY means the GPU
// has not reached one of them yet, which a frame timer reports as "no value" rather than stalling.
extern "C" jlong awake_vulkan_queries_ticks_between(
        JNIEnv*,
        jlong device,
        jlong queryPool,
        jint firstQuery,
        jint validBits) {
    uint64_t stamps[2] = {0, 0};
    VkResult result = vkGetQueryPoolResults(
            reinterpret_cast<VkDevice>(device),
            reinterpret_cast<VkQueryPool>(queryPool),
            static_cast<uint32_t>(firstQuery),
            2,
            sizeof(stamps),
            stamps,
            sizeof(uint64_t),
            VK_QUERY_RESULT_64_BIT);
    if (result != VK_SUCCESS) return -1;
    const uint64_t mask = validBits >= 64 ? ~0ULL : ((1ULL << validBits) - 1ULL);
    const uint64_t start = stamps[0] & mask;
    const uint64_t end = stamps[1] & mask;
    // A counter narrower than 64 bits wraps; the difference modulo its width is still right.
    return static_cast<jlong>((end - start) & mask);
}
