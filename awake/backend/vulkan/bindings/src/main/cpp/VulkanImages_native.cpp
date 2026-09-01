/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <cstdint>

#include <jni.h>
#include <vulkan/vulkan.h>

#include "jni-utils.h"

extern "C" void awake_vulkan_images_transition_image_layout(
        JNIEnv* env,
        jlong commandBuffer,
        jlong image,
        jint oldLayout,
        jint newLayout,
        jint levelCount,
        jint layerCount) {
    if (!commandBuffer) {
        throw_illegal_state(env, "vkTransitionImageLayout: commandBuffer not initialized");
        return;
    }
    if (!image) {
        throw_illegal_state(env, "vkTransitionImageLayout: image not initialized");
        return;
    }

    VkImageLayout oldVkLayout = static_cast<VkImageLayout>(oldLayout);
    VkImageLayout newVkLayout = static_cast<VkImageLayout>(newLayout);

    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = oldVkLayout;
    barrier.newLayout = newVkLayout;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = reinterpret_cast<VkImage>(image);
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel = 0;
    barrier.subresourceRange.levelCount = static_cast<uint32_t>(levelCount);
    barrier.subresourceRange.baseArrayLayer = 0;
    // Every layer, not just 0. A 2D-array image transitioned with layerCount = 1 leaves its
    // upper layers in UNDEFINED, and sampling those returns garbage with no validation error.
    barrier.subresourceRange.layerCount = static_cast<uint32_t>(layerCount);

    VkPipelineStageFlags srcStage;
    VkPipelineStageFlags dstStage;

    if (oldVkLayout == VK_IMAGE_LAYOUT_UNDEFINED && newVkLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask = 0;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldVkLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newVkLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    } else if (oldVkLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL && newVkLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    } else if (oldVkLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && newVkLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
        barrier.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        dstStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    } else if (oldVkLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && newVkLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldVkLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && newVkLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    } else if (oldVkLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR && newVkLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
        // Reading back a frame that the render pass already left in PRESENT_SRC. Only a headless
        // stand-in image is ever read this way -- a real presented image belongs to the
        // presentation engine by this point.
        barrier.srcAccessMask = 0;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        srcStage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
        dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldVkLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && newVkLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        barrier.dstAccessMask = 0;
        srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        dstStage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
    } else {
        throw_illegal_argument(env, "vkTransitionImageLayout: unsupported layout transition");
        return;
    }

    vkCmdPipelineBarrier(
            reinterpret_cast<VkCommandBuffer>(commandBuffer),
            srcStage, dstStage,
            0,
            0, nullptr,
            0, nullptr,
            1, &barrier);
}
