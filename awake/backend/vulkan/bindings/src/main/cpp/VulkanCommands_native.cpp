/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <cstdint>
#include <string>
#include <vector>

#include <jni.h>
#include <vulkan/vulkan.h>

#include "jni-utils.h"
#include "exception_utils.h"
#include "includes/VulkanUtils.h"

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkBeginCommandBuffer(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jint flags) {
    if (!commandBuffer) {
        throw_illegal_state(env, "vkBeginCommandBuffer: commandBuffer not initialized");
        return;
    }
    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = static_cast<VkCommandBufferUsageFlags>(flags);
    VkResult result = vkBeginCommandBuffer(
        reinterpret_cast<VkCommandBuffer>(commandBuffer),
        &beginInfo);
    if (result != VK_SUCCESS) {
        exception_utils::resultException(env, result,
            "There was a problem executing vkBeginCommandBuffer");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCmdBindGraphicsPipeline(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jlong pipeline) {
    if (!commandBuffer) {
        throw_illegal_state(env, "vkCmdBindGraphicsPipeline: commandBuffer not initialized");
        return;
    }
    if (!pipeline) {
        throw_illegal_state(env, "vkCmdBindGraphicsPipeline: pipeline not initialized");
        return;
    }
    vkCmdBindPipeline(
        reinterpret_cast<VkCommandBuffer>(commandBuffer),
        VK_PIPELINE_BIND_POINT_GRAPHICS,
        reinterpret_cast<VkPipeline>(pipeline));
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCmdSetViewportRect(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jfloat x,
        jfloat y,
        jfloat width,
        jfloat height,
        jfloat minDepth,
        jfloat maxDepth) {
    if (!commandBuffer) {
        throw_illegal_state(env, "vkCmdSetViewportRect: commandBuffer not initialized");
        return;
    }
    VkViewport viewport{};
    viewport.x = x;
    viewport.y = y;
    viewport.width = width;
    viewport.height = height;
    viewport.minDepth = minDepth;
    viewport.maxDepth = maxDepth;
    vkCmdSetViewport(reinterpret_cast<VkCommandBuffer>(commandBuffer), 0, 1, &viewport);
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCmdSetScissorRect(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jint x,
        jint y,
        jint width,
        jint height) {
    if (!commandBuffer) {
        throw_illegal_state(env, "vkCmdSetScissorRect: commandBuffer not initialized");
        return;
    }
    VkRect2D scissor{};
    scissor.offset.x = x;
    scissor.offset.y = y;
    scissor.extent.width = static_cast<uint32_t>(width);
    scissor.extent.height = static_cast<uint32_t>(height);
    vkCmdSetScissor(reinterpret_cast<VkCommandBuffer>(commandBuffer), 0, 1, &scissor);
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCmdBeginRenderPassEmpty(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jlong renderPass,
        jlong framebuffer,
        jint width,
        jint height) {
    if (!commandBuffer || !renderPass || !framebuffer) {
        throw_illegal_state(env, "vkCmdBeginRenderPassEmpty: handle not initialized");
        return;
    }
    VkRenderPassBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    beginInfo.renderPass = reinterpret_cast<VkRenderPass>(renderPass);
    beginInfo.framebuffer = reinterpret_cast<VkFramebuffer>(framebuffer);
    beginInfo.renderArea.offset = {0, 0};
    beginInfo.renderArea.extent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height)};
    beginInfo.clearValueCount = 0;
    beginInfo.pClearValues = nullptr;
    vkCmdBeginRenderPass(
        reinterpret_cast<VkCommandBuffer>(commandBuffer),
        &beginInfo,
        VK_SUBPASS_CONTENTS_INLINE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCmdBeginRenderPassColor(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jlong renderPass,
        jlong framebuffer,
        jint width,
        jint height,
        jfloat r,
        jfloat g,
        jfloat b,
        jfloat a) {
    if (!commandBuffer || !renderPass || !framebuffer) {
        throw_illegal_state(env, "vkCmdBeginRenderPassColor: handle not initialized");
        return;
    }
    VkClearValue clear{};
    clear.color.float32[0] = r;
    clear.color.float32[1] = g;
    clear.color.float32[2] = b;
    clear.color.float32[3] = a;

    VkRenderPassBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    beginInfo.renderPass = reinterpret_cast<VkRenderPass>(renderPass);
    beginInfo.framebuffer = reinterpret_cast<VkFramebuffer>(framebuffer);
    beginInfo.renderArea.offset = {0, 0};
    beginInfo.renderArea.extent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height)};
    beginInfo.clearValueCount = 1;
    beginInfo.pClearValues = &clear;
    vkCmdBeginRenderPass(
        reinterpret_cast<VkCommandBuffer>(commandBuffer),
        &beginInfo,
        VK_SUBPASS_CONTENTS_INLINE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCmdBeginRenderPassDepth(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jlong renderPass,
        jlong framebuffer,
        jint width,
        jint height,
        jfloat depth) {
    if (!commandBuffer || !renderPass || !framebuffer) {
        throw_illegal_state(env, "vkCmdBeginRenderPassDepth: handle not initialized");
        return;
    }
    VkClearValue clear{};
    clear.depthStencil.depth = depth;
    clear.depthStencil.stencil = 0;

    VkRenderPassBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    beginInfo.renderPass = reinterpret_cast<VkRenderPass>(renderPass);
    beginInfo.framebuffer = reinterpret_cast<VkFramebuffer>(framebuffer);
    beginInfo.renderArea.offset = {0, 0};
    beginInfo.renderArea.extent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height)};
    beginInfo.clearValueCount = 1;
    beginInfo.pClearValues = &clear;
    vkCmdBeginRenderPass(
        reinterpret_cast<VkCommandBuffer>(commandBuffer),
        &beginInfo,
        VK_SUBPASS_CONTENTS_INLINE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCmdBeginRenderPassColorDepth(
        JNIEnv* env,
        jclass,
        jlong commandBuffer,
        jlong renderPass,
        jlong framebuffer,
        jint width,
        jint height,
        jfloat r,
        jfloat g,
        jfloat b,
        jfloat a,
        jfloat depth) {
    if (!commandBuffer || !renderPass || !framebuffer) {
        throw_illegal_state(env, "vkCmdBeginRenderPassColorDepth: handle not initialized");
        return;
    }
    VkClearValue clears[2]{};
    clears[0].color.float32[0] = r;
    clears[0].color.float32[1] = g;
    clears[0].color.float32[2] = b;
    clears[0].color.float32[3] = a;
    clears[1].depthStencil.depth = depth;
    clears[1].depthStencil.stencil = 0;

    VkRenderPassBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    beginInfo.renderPass = reinterpret_cast<VkRenderPass>(renderPass);
    beginInfo.framebuffer = reinterpret_cast<VkFramebuffer>(framebuffer);
    beginInfo.renderArea.offset = {0, 0};
    beginInfo.renderArea.extent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height)};
    beginInfo.clearValueCount = 2;
    beginInfo.pClearValues = clears;
    vkCmdBeginRenderPass(
        reinterpret_cast<VkCommandBuffer>(commandBuffer),
        &beginInfo,
        VK_SUBPASS_CONTENTS_INLINE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkWaitForFence(
        JNIEnv* env,
        jclass,
        jlong device,
        jlong fence,
        jlong timeout) {
    if (!device || !fence) {
        throw_illegal_state(env, "vkWaitForFence: handle not initialized");
        return;
    }
    VkFence vkFence = reinterpret_cast<VkFence>(fence);
    vkWaitForFences(
        reinterpret_cast<VkDevice>(device),
        1,
        &vkFence,
        VK_TRUE,
        static_cast<uint64_t>(timeout));
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkResetFence(
        JNIEnv* env,
        jclass,
        jlong device,
        jlong fence) {
    if (!device || !fence) {
        throw_illegal_state(env, "vkResetFence: handle not initialized");
        return;
    }
    VkFence vkFence = reinterpret_cast<VkFence>(fence);
    VkResult result = vkResetFences(reinterpret_cast<VkDevice>(device), 1, &vkFence);
    if (result != VK_SUCCESS) {
        exception_utils::resultException(env, result,
            "There was a problem executing vkResetFences");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkQueueSubmitSingle(
        JNIEnv* env,
        jclass,
        jlong queue,
        jlong commandBuffer,
        jlong waitSemaphore,
        jint waitStageMask,
        jlong signalSemaphore,
        jlong fence) {
    if (!queue || !commandBuffer) {
        throw_illegal_state(env, "vkQueueSubmitSingle: queue or commandBuffer not initialized");
        return;
    }
    VkCommandBuffer vkCmd = reinterpret_cast<VkCommandBuffer>(commandBuffer);
    VkSemaphore vkWaitSem = reinterpret_cast<VkSemaphore>(waitSemaphore);
    VkPipelineStageFlags vkWaitStage = static_cast<VkPipelineStageFlags>(waitStageMask);
    VkSemaphore vkSignalSem = reinterpret_cast<VkSemaphore>(signalSemaphore);
    VkFence vkFence = fence ? reinterpret_cast<VkFence>(fence) : VK_NULL_HANDLE;

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    if (waitSemaphore) {
        submitInfo.waitSemaphoreCount = 1;
        submitInfo.pWaitSemaphores = &vkWaitSem;
        submitInfo.pWaitDstStageMask = &vkWaitStage;
    }
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &vkCmd;
    if (signalSemaphore) {
        submitInfo.signalSemaphoreCount = 1;
        submitInfo.pSignalSemaphores = &vkSignalSem;
    }

    VkResult result = vkQueueSubmit(reinterpret_cast<VkQueue>(queue), 1, &submitInfo, vkFence);
    if (result != VK_SUCCESS) {
        exception_utils::resultException(env, result,
            "There was a problem executing vkQueueSubmit");
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_nativeQueuePresentSingleStatus(
        JNIEnv* env,
        jclass,
        jlong queue,
        jlong swapchain,
        jint imageIndex,
        jlong waitSemaphore) {
    if (!queue || !swapchain) {
        throw_illegal_state(env, "vkQueuePresentSingleStatus: queue or swapchain not initialized");
        return static_cast<jint>(VK_ERROR_INITIALIZATION_FAILED);
    }
    VkSemaphore vkWaitSem = reinterpret_cast<VkSemaphore>(waitSemaphore);
    VkSwapchainKHR vkSwapchain = reinterpret_cast<VkSwapchainKHR>(swapchain);
    uint32_t vkImageIndex = static_cast<uint32_t>(imageIndex);

    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    if (waitSemaphore) {
        presentInfo.waitSemaphoreCount = 1;
        presentInfo.pWaitSemaphores = &vkWaitSem;
    }
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = &vkSwapchain;
    presentInfo.pImageIndices = &vkImageIndex;
    presentInfo.pResults = nullptr;

    VkResult result = awake::presentWithHook(reinterpret_cast<VkQueue>(queue), &presentInfo);
    if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR && result != VK_ERROR_OUT_OF_DATE_KHR) {
        exception_utils::resultException(env, result,
            "There was a problem executing vkQueuePresentKHR");
    }
    return static_cast<jint>(result);
}

namespace {
constexpr int32_t FEAT10_FULL_DRAW_INDEX_UINT32 = 1 << 0;
constexpr int32_t FEAT10_MULTI_DRAW_INDIRECT = 1 << 1;
constexpr int32_t FEAT10_DEPTH_CLAMP = 1 << 2;
constexpr int32_t FEAT10_FILL_MODE_NON_SOLID = 1 << 3;
constexpr int32_t FEAT10_SAMPLER_ANISOTROPY = 1 << 4;
constexpr int32_t FEAT10_TEXTURE_COMPRESSION_ETC2 = 1 << 5;
constexpr int32_t FEAT10_TEXTURE_COMPRESSION_ASTC_LDR = 1 << 6;
constexpr int32_t FEAT10_TEXTURE_COMPRESSION_BC = 1 << 7;

constexpr int32_t MODERN_TIMELINE_SEMAPHORE = 1 << 0;
constexpr int32_t MODERN_DESCRIPTOR_INDEXING = 1 << 1;
constexpr int32_t MODERN_BUFFER_DEVICE_ADDRESS = 1 << 2;
constexpr int32_t MODERN_DRAW_INDIRECT_COUNT = 1 << 3;
constexpr int32_t MODERN_SCALAR_BLOCK_LAYOUT = 1 << 4;
constexpr int32_t MODERN_SYNCHRONIZATION_2 = 1 << 5;
constexpr int32_t MODERN_DYNAMIC_RENDERING = 1 << 6;
constexpr int32_t MODERN_MAINTENANCE_4 = 1 << 7;
constexpr int32_t MODERN_PIPELINE_CREATION_CACHE_CONTROL = 1 << 8;

constexpr int32_t MODERN_12_MASK =
    MODERN_TIMELINE_SEMAPHORE |
    MODERN_DESCRIPTOR_INDEXING |
    MODERN_BUFFER_DEVICE_ADDRESS |
    MODERN_DRAW_INDIRECT_COUNT |
    MODERN_SCALAR_BLOCK_LAYOUT;

constexpr int32_t MODERN_13_MASK =
    MODERN_SYNCHRONIZATION_2 |
    MODERN_DYNAMIC_RENDERING |
    MODERN_MAINTENANCE_4 |
    MODERN_PIPELINE_CREATION_CACHE_CONTROL;

VkPhysicalDeviceFeatures unpackFeatures10(jint mask) {
    VkPhysicalDeviceFeatures f{};
    f.fullDrawIndexUint32 = (mask & FEAT10_FULL_DRAW_INDEX_UINT32) ? VK_TRUE : VK_FALSE;
    f.multiDrawIndirect = (mask & FEAT10_MULTI_DRAW_INDIRECT) ? VK_TRUE : VK_FALSE;
    f.depthClamp = (mask & FEAT10_DEPTH_CLAMP) ? VK_TRUE : VK_FALSE;
    f.fillModeNonSolid = (mask & FEAT10_FILL_MODE_NON_SOLID) ? VK_TRUE : VK_FALSE;
    f.samplerAnisotropy = (mask & FEAT10_SAMPLER_ANISOTROPY) ? VK_TRUE : VK_FALSE;
    f.textureCompressionETC2 = (mask & FEAT10_TEXTURE_COMPRESSION_ETC2) ? VK_TRUE : VK_FALSE;
    f.textureCompressionASTC_LDR = (mask & FEAT10_TEXTURE_COMPRESSION_ASTC_LDR) ? VK_TRUE : VK_FALSE;
    f.textureCompressionBC = (mask & FEAT10_TEXTURE_COMPRESSION_BC) ? VK_TRUE : VK_FALSE;
    return f;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCreatePipelineCacheFromBytes(
        JNIEnv* env,
        jclass,
        jlong device,
        jbyteArray initialData) {
    if (!device) {
        throw_illegal_state(env, "vkCreatePipelineCacheFromBytes: device not initialized");
        return 0;
    }
    VkPipelineCacheCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO;
    jbyte* bytes = nullptr;
    if (initialData != nullptr) {
        jsize len = env->GetArrayLength(initialData);
        if (len > 0) {
            bytes = env->GetByteArrayElements(initialData, nullptr);
            if (!bytes) return 0;
            createInfo.initialDataSize = static_cast<size_t>(len);
            createInfo.pInitialData = bytes;
        }
    }
    VkPipelineCache cache = VK_NULL_HANDLE;
    VkResult result = vkCreatePipelineCache(
        reinterpret_cast<VkDevice>(device), &createInfo, nullptr, &cache);
    if (bytes != nullptr) {
        env->ReleaseByteArrayElements(initialData, bytes, JNI_ABORT);
    }
    if (result != VK_SUCCESS) {
        exception_utils::resultException(env, result,
            "There was a problem executing vkCreatePipelineCache");
        return 0;
    }
    return reinterpret_cast<jlong>(cache);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkGetPipelineCacheData(
        JNIEnv* env,
        jclass,
        jlong device,
        jlong pipelineCache) {
    if (!device || !pipelineCache) {
        throw_illegal_state(env, "vkGetPipelineCacheData: handle not initialized");
        return nullptr;
    }
    VkDevice vkDevice = reinterpret_cast<VkDevice>(device);
    VkPipelineCache vkCache = reinterpret_cast<VkPipelineCache>(pipelineCache);

    size_t dataSize = 0;
    VkResult result = vkGetPipelineCacheData(vkDevice, vkCache, &dataSize, nullptr);
    if (result != VK_SUCCESS) {
        exception_utils::resultException(env, result,
            "There was a problem querying vkGetPipelineCacheData size");
        return nullptr;
    }
    if (dataSize == 0) {
        return env->NewByteArray(0);
    }
    std::vector<uint8_t> buffer(dataSize);
    result = vkGetPipelineCacheData(vkDevice, vkCache, &dataSize, buffer.data());
    if (result != VK_SUCCESS && result != VK_INCOMPLETE) {
        exception_utils::resultException(env, result,
            "There was a problem reading vkGetPipelineCacheData");
        return nullptr;
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(dataSize));
    if (!out) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(dataSize),
        reinterpret_cast<const jbyte*>(buffer.data()));
    return out;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkQueryModernFeatures(
        JNIEnv* env,
        jclass,
        jlong instance,
        jlong physicalDevice,
        jint apiVersion) {
    if (!instance || !physicalDevice) {
        throw_illegal_state(env, "vkQueryModernFeatures: handle not initialized");
        return 0;
    }
#ifdef VK_VERSION_1_2
    const uint32_t ver = static_cast<uint32_t>(apiVersion);
    if (ver < VK_API_VERSION_1_2) {
        return 0;
    }
    auto getFeatures2 = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
        vkGetInstanceProcAddr(reinterpret_cast<VkInstance>(instance), "vkGetPhysicalDeviceFeatures2"));
    if (!getFeatures2) {
        return 0;
    }

    VkPhysicalDeviceVulkan12Features f12{};
    f12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;

#ifdef VK_VERSION_1_3
    VkPhysicalDeviceVulkan13Features f13{};
    if (ver >= VK_API_VERSION_1_3) {
        f13.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
        f12.pNext = &f13;
    }
#endif

    VkPhysicalDeviceFeatures2 f2{};
    f2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
    f2.pNext = &f12;

    getFeatures2(reinterpret_cast<VkPhysicalDevice>(physicalDevice), &f2);

    int32_t mask = 0;
    if (f12.timelineSemaphore == VK_TRUE) mask |= MODERN_TIMELINE_SEMAPHORE;
    if (f12.descriptorIndexing == VK_TRUE &&
        f12.shaderSampledImageArrayNonUniformIndexing == VK_TRUE &&
        f12.descriptorBindingSampledImageUpdateAfterBind == VK_TRUE &&
        f12.descriptorBindingPartiallyBound == VK_TRUE &&
        f12.descriptorBindingVariableDescriptorCount == VK_TRUE &&
        f12.runtimeDescriptorArray == VK_TRUE) {
        mask |= MODERN_DESCRIPTOR_INDEXING;
    }
    if (f12.bufferDeviceAddress == VK_TRUE) mask |= MODERN_BUFFER_DEVICE_ADDRESS;
    if (f12.drawIndirectCount == VK_TRUE) mask |= MODERN_DRAW_INDIRECT_COUNT;
    if (f12.scalarBlockLayout == VK_TRUE) mask |= MODERN_SCALAR_BLOCK_LAYOUT;

#ifdef VK_VERSION_1_3
    if (ver >= VK_API_VERSION_1_3) {
        if (f13.synchronization2 == VK_TRUE) mask |= MODERN_SYNCHRONIZATION_2;
        if (f13.dynamicRendering == VK_TRUE) mask |= MODERN_DYNAMIC_RENDERING;
        if (f13.maintenance4 == VK_TRUE) mask |= MODERN_MAINTENANCE_4;
        if (f13.pipelineCreationCacheControl == VK_TRUE) mask |= MODERN_PIPELINE_CREATION_CACHE_CONTROL;
    }
#endif
    return static_cast<jint>(mask);
#else
    (void)apiVersion;
    return 0;
#endif
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_awakekt_awake_vulkan_gen_VulkanCommands_vkCreateLogicalDeviceWithFeatures(
        JNIEnv* env,
        jclass,
        jlong physicalDevice,
        jintArray queueFamilyIndices,
        jobjectArray enabledExtensions,
        jint features10Mask,
        jint apiVersion,
        jint modernFeaturesMask) {
    if (!physicalDevice) {
        throw_illegal_state(env, "vkCreateLogicalDeviceWithFeatures: physicalDevice not initialized");
        return 0;
    }
    if (!queueFamilyIndices) {
        throw_illegal_argument(env, "vkCreateLogicalDeviceWithFeatures: queueFamilyIndices must not be null");
        return 0;
    }

    const jsize queueCount = env->GetArrayLength(queueFamilyIndices);
    std::vector<jint> queueFamilies(static_cast<size_t>(queueCount));
    if (queueCount > 0) {
        env->GetIntArrayRegion(queueFamilyIndices, 0, queueCount, queueFamilies.data());
    }
    const float queuePriority = 1.0f;
    std::vector<VkDeviceQueueCreateInfo> queueCreateInfos;
    queueCreateInfos.reserve(queueFamilies.size());
    for (jint family : queueFamilies) {
        VkDeviceQueueCreateInfo qci{};
        qci.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        qci.queueFamilyIndex = static_cast<uint32_t>(family);
        qci.queueCount = 1;
        qci.pQueuePriorities = &queuePriority;
        queueCreateInfos.push_back(qci);
    }

    std::vector<std::string> extStorage;
    std::vector<const char*> extPtrs;
    if (enabledExtensions != nullptr) {
        const jsize extCount = env->GetArrayLength(enabledExtensions);
        extStorage.reserve(static_cast<size_t>(extCount));
        extPtrs.reserve(static_cast<size_t>(extCount));
        for (jsize i = 0; i < extCount; ++i) {
            auto jstr = static_cast<jstring>(env->GetObjectArrayElement(enabledExtensions, i));
            if (jstr) {
                const char* chars = env->GetStringUTFChars(jstr, nullptr);
                if (chars) {
                    extStorage.emplace_back(chars);
                    env->ReleaseStringUTFChars(jstr, chars);
                }
                env->DeleteLocalRef(jstr);
            }
        }
        for (const auto& s : extStorage) {
            extPtrs.push_back(s.c_str());
        }
    }

    VkPhysicalDeviceFeatures features10 = unpackFeatures10(features10Mask);
    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.queueCreateInfoCount = static_cast<uint32_t>(queueCreateInfos.size());
    createInfo.pQueueCreateInfos = queueCreateInfos.data();
    createInfo.enabledExtensionCount = static_cast<uint32_t>(extPtrs.size());
    createInfo.ppEnabledExtensionNames = extPtrs.empty() ? nullptr : extPtrs.data();

#ifdef VK_VERSION_1_2
    const uint32_t ver = static_cast<uint32_t>(apiVersion);
    const bool enable12 = ver >= VK_API_VERSION_1_2 && (modernFeaturesMask & MODERN_12_MASK) != 0;
#ifdef VK_VERSION_1_3
    const bool enable13 = ver >= VK_API_VERSION_1_3 && (modernFeaturesMask & MODERN_13_MASK) != 0;
#else
    const bool enable13 = false;
#endif

    VkPhysicalDeviceFeatures2 features2{};
    VkPhysicalDeviceVulkan12Features f12{};
#ifdef VK_VERSION_1_3
    VkPhysicalDeviceVulkan13Features f13{};
#endif

    if (enable12 || enable13) {
        features2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
        features2.features = features10;
        void** tail = &features2.pNext;

        if (enable12) {
            f12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
            if (modernFeaturesMask & MODERN_TIMELINE_SEMAPHORE) f12.timelineSemaphore = VK_TRUE;
            if (modernFeaturesMask & MODERN_DESCRIPTOR_INDEXING) {
                f12.descriptorIndexing = VK_TRUE;
                f12.shaderSampledImageArrayNonUniformIndexing = VK_TRUE;
                f12.descriptorBindingSampledImageUpdateAfterBind = VK_TRUE;
                f12.descriptorBindingPartiallyBound = VK_TRUE;
                f12.descriptorBindingVariableDescriptorCount = VK_TRUE;
                f12.runtimeDescriptorArray = VK_TRUE;
            }
            if (modernFeaturesMask & MODERN_BUFFER_DEVICE_ADDRESS) f12.bufferDeviceAddress = VK_TRUE;
            if (modernFeaturesMask & MODERN_DRAW_INDIRECT_COUNT) f12.drawIndirectCount = VK_TRUE;
            if (modernFeaturesMask & MODERN_SCALAR_BLOCK_LAYOUT) f12.scalarBlockLayout = VK_TRUE;
            *tail = &f12;
            tail = &f12.pNext;
        }
#ifdef VK_VERSION_1_3
        if (enable13) {
            f13.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
            if (modernFeaturesMask & MODERN_SYNCHRONIZATION_2) f13.synchronization2 = VK_TRUE;
            if (modernFeaturesMask & MODERN_DYNAMIC_RENDERING) f13.dynamicRendering = VK_TRUE;
            if (modernFeaturesMask & MODERN_MAINTENANCE_4) f13.maintenance4 = VK_TRUE;
            if (modernFeaturesMask & MODERN_PIPELINE_CREATION_CACHE_CONTROL) {
                f13.pipelineCreationCacheControl = VK_TRUE;
            }
            *tail = &f13;
            tail = &f13.pNext;
        }
#endif
        createInfo.pNext = &features2;
        createInfo.pEnabledFeatures = nullptr;
    } else {
        createInfo.pNext = nullptr;
        createInfo.pEnabledFeatures = &features10;
    }
#else
    (void)apiVersion;
    (void)modernFeaturesMask;
    createInfo.pNext = nullptr;
    createInfo.pEnabledFeatures = &features10;
#endif

    VkDevice vkDevice = VK_NULL_HANDLE;
    VkResult result = vkCreateDevice(
        reinterpret_cast<VkPhysicalDevice>(physicalDevice), &createInfo, nullptr, &vkDevice);
    if (result != VK_SUCCESS) {
        exception_utils::resultException(env, result,
            "There was a problem executing vkCreateDevice");
        return 0;
    }
    return reinterpret_cast<jlong>(vkDevice);
}
