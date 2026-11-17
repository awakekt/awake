/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// JNI entry points over Swappy's Vulkan API (games-frame-pacing), Android only (#1699). Nothing
// calls them yet: the Kotlin wiring behind the Android frame-pacing setting is #1699's next PR.
//
// Swappy's API is C (extern "C", Vulkan handles and scalars), and only that crosses between this
// library and libswappy_static.a: both link the NDK's static libc++, and no C++ runtime type is
// passed between them. scripts/swappy-symbols.sh checks the built library for it.

#include <cstring>
#include <mutex>
#include <unordered_map>
#include <vector>

#include <android/native_window_jni.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include <swappy/swappyVk.h>

#include <includes/VulkanUtils.h>

namespace {

// The window each swapchain was given, held until Swappy forgets that swapchain.
std::mutex windowsLock;
std::unordered_map<VkSwapchainKHR, ANativeWindow *> windows;

template <typename T>
T handle(jlong value) { return reinterpret_cast<T>(value); }

} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_determineDeviceExtensions(
        JNIEnv *env, jobject, jlong physicalDevice, jobjectArray available) {
    jsize count = env->GetArrayLength(available);
    std::vector<VkExtensionProperties> properties(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto name = static_cast<jstring>(env->GetObjectArrayElement(available, i));
        const char *chars = env->GetStringUTFChars(name, nullptr);
        strncpy(properties[i].extensionName, chars, VK_MAX_EXTENSION_NAME_SIZE - 1);
        env->ReleaseStringUTFChars(name, chars);
        env->DeleteLocalRef(name);
    }
    uint32_t required = 0;
    SwappyVk_determineDeviceExtensions(handle<VkPhysicalDevice>(physicalDevice),
                                       static_cast<uint32_t>(count), properties.data(), &required, nullptr);
    std::vector<std::vector<char>> buffers(required, std::vector<char>(VK_MAX_EXTENSION_NAME_SIZE, 0));
    std::vector<char *> names(required);
    for (uint32_t i = 0; i < required; ++i) names[i] = buffers[i].data();
    SwappyVk_determineDeviceExtensions(handle<VkPhysicalDevice>(physicalDevice),
                                       static_cast<uint32_t>(count), properties.data(), &required, names.data());
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(required), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    for (uint32_t i = 0; i < required; ++i) {
        jstring name = env->NewStringUTF(names[i]);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), name);
        env->DeleteLocalRef(name);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_setQueueFamilyIndex(
        JNIEnv *, jobject, jlong device, jlong queue, jint queueFamilyIndex) {
    SwappyVk_setQueueFamilyIndex(handle<VkDevice>(device), handle<VkQueue>(queue),
                                 static_cast<uint32_t>(queueFamilyIndex));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_initAndGetRefreshCycleDuration(
        JNIEnv *env, jobject, jobject activity, jlong physicalDevice, jlong device, jlong swapchain) {
    uint64_t refreshDuration = 0;
    bool initialised = SwappyVk_initAndGetRefreshCycleDuration(
            env, activity, handle<VkPhysicalDevice>(physicalDevice), handle<VkDevice>(device),
            handle<VkSwapchainKHR>(swapchain), &refreshDuration);
    return initialised ? static_cast<jlong>(refreshDuration) : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_setWindow(
        JNIEnv *env, jobject, jlong device, jlong swapchain, jobject surface) {
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    SwappyVk_setWindow(handle<VkDevice>(device), handle<VkSwapchainKHR>(swapchain), window);
    std::lock_guard<std::mutex> lock(windowsLock);
    auto key = handle<VkSwapchainKHR>(swapchain);
    auto previous = windows.find(key);
    if (previous != windows.end() && previous->second != nullptr) ANativeWindow_release(previous->second);
    windows[key] = window;
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_setSwapIntervalNs(
        JNIEnv *, jobject, jlong device, jlong swapchain, jlong swapIntervalNs) {
    SwappyVk_setSwapIntervalNS(handle<VkDevice>(device), handle<VkSwapchainKHR>(swapchain),
                               static_cast<uint64_t>(swapIntervalNs));
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_destroySwapchain(
        JNIEnv *, jobject, jlong device, jlong swapchain) {
    SwappyVk_destroySwapchain(handle<VkDevice>(device), handle<VkSwapchainKHR>(swapchain));
    std::lock_guard<std::mutex> lock(windowsLock);
    auto key = handle<VkSwapchainKHR>(swapchain);
    auto held = windows.find(key);
    if (held != windows.end()) {
        if (held->second != nullptr) ANativeWindow_release(held->second);
        windows.erase(held);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_destroyDevice(JNIEnv *, jobject, jlong device) {
    SwappyVk_destroyDevice(handle<VkDevice>(device));
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_vulkan_swappy_SwappyVk_setPresentsThroughSwappy(
        JNIEnv *, jobject, jboolean enabled) {
    awake::setPresentHook(enabled ? &SwappyVk_queuePresent : nullptr);
}
