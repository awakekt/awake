/*
 *  VkSubmitInfoAccessor.cpp
 *  Vulkan accessor for VkSubmitInfo
 *  Created by Ron June Valdoz */

#include <includes/VkSubmitInfoAccessor.h>

VkSubmitInfoAccessor::VkSubmitInfoAccessor(JNIEnv *env, jobject obj) : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    pWaitSemaphoresField = env->GetFieldID(clazz, "pWaitSemaphores", "[Ljava/lang/Long;");
    pWaitDstStageMaskField = env->GetFieldID(clazz, "pWaitDstStageMask", "[I");
    pCommandBuffersField = env->GetFieldID(clazz, "pCommandBuffers", "[Ljava/lang/Long;");
    pSignalSemaphoresField = env->GetFieldID(clazz, "pSignalSemaphores", "[Ljava/lang/Long;");
}

VkStructureType
VkSubmitInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

void
VkSubmitInfoAccessor::getpNext(VkSubmitInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkSubmitInfoAccessor::getpSignalSemaphores(VkSubmitInfo &clazzInfo) {
    signalSemaphoresStorage.clear();
    auto pSignalSemaphoresArray = (jobjectArray) env->GetObjectField(obj, pSignalSemaphoresField);
    if (pSignalSemaphoresArray == nullptr) {
        clazzInfo.signalSemaphoreCount = 0;
        clazzInfo.pSignalSemaphores = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pSignalSemaphoresArray);
    if (size > 0) {
        signalSemaphoresStorage.reserve(static_cast<size_t>(size));
        jclass longClass = env->FindClass("java/lang/Long");
        jmethodID getValueMethod = env->GetMethodID(longClass, "longValue", "()J");
        for (int i = 0; i < size; ++i) {
            auto element = env->GetObjectArrayElement(pSignalSemaphoresArray, i);
            jlong value = env->CallLongMethod(element, getValueMethod);
            signalSemaphoresStorage.push_back(reinterpret_cast<VkSemaphore>(value));
            env->DeleteLocalRef(element);
        }
        env->DeleteLocalRef(longClass);
    }
    clazzInfo.signalSemaphoreCount = static_cast<uint32_t>(signalSemaphoresStorage.size());
    clazzInfo.pSignalSemaphores = signalSemaphoresStorage.empty() ? nullptr : signalSemaphoresStorage.data();
    env->DeleteLocalRef(pSignalSemaphoresArray);
}

void
VkSubmitInfoAccessor::getpWaitDstStageMask(VkSubmitInfo &clazzInfo) {
    waitDstStageMaskStorage.clear();
    auto pWaitDstStageMaskArray = (jintArray) env->GetObjectField(obj, pWaitDstStageMaskField);
    if (pWaitDstStageMaskArray == nullptr) {
        clazzInfo.pWaitDstStageMask = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pWaitDstStageMaskArray);
    if (size > 0) {
        waitDstStageMaskStorage.resize(static_cast<size_t>(size));
        env->GetIntArrayRegion(pWaitDstStageMaskArray, 0, size,
                               reinterpret_cast<jint *>(waitDstStageMaskStorage.data()));
    }
    clazzInfo.pWaitDstStageMask = waitDstStageMaskStorage.empty() ? nullptr : waitDstStageMaskStorage.data();
    env->DeleteLocalRef(pWaitDstStageMaskArray);
}

void
VkSubmitInfoAccessor::fromObject(VkSubmitInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    getpWaitSemaphores(clazzInfo);  // Long Object Array
    getpWaitDstStageMask(clazzInfo);  // int Object Array
    getpCommandBuffers(clazzInfo);  // Long Object Array
    getpSignalSemaphores(clazzInfo);  // Long Object Array
}

void
VkSubmitInfoAccessor::getpCommandBuffers(VkSubmitInfo &clazzInfo) {
    commandBuffersStorage.clear();
    auto pCommandBuffersArray = (jobjectArray) env->GetObjectField(obj, pCommandBuffersField);
    if (pCommandBuffersArray == nullptr) {
        clazzInfo.commandBufferCount = 0;
        clazzInfo.pCommandBuffers = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pCommandBuffersArray);
    if (size > 0) {
        commandBuffersStorage.reserve(static_cast<size_t>(size));
        jclass longClass = env->FindClass("java/lang/Long");
        jmethodID getValueMethod = env->GetMethodID(longClass, "longValue", "()J");
        for (int i = 0; i < size; ++i) {
            auto element = env->GetObjectArrayElement(pCommandBuffersArray, i);
            jlong value = env->CallLongMethod(element, getValueMethod);
            commandBuffersStorage.push_back(reinterpret_cast<VkCommandBuffer>(value));
            env->DeleteLocalRef(element);
        }
        env->DeleteLocalRef(longClass);
    }
    clazzInfo.commandBufferCount = static_cast<uint32_t>(commandBuffersStorage.size());
    clazzInfo.pCommandBuffers = commandBuffersStorage.empty() ? nullptr : commandBuffersStorage.data();
    env->DeleteLocalRef(pCommandBuffersArray);
}

void
VkSubmitInfoAccessor::getpWaitSemaphores(VkSubmitInfo &clazzInfo) {
    waitSemaphoresStorage.clear();
    auto pWaitSemaphoresArray = (jobjectArray) env->GetObjectField(obj, pWaitSemaphoresField);
    if (pWaitSemaphoresArray == nullptr) {
        clazzInfo.waitSemaphoreCount = 0;
        clazzInfo.pWaitSemaphores = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pWaitSemaphoresArray);
    if (size > 0) {
        waitSemaphoresStorage.reserve(static_cast<size_t>(size));
        jclass longClass = env->FindClass("java/lang/Long");
        jmethodID getValueMethod = env->GetMethodID(longClass, "longValue", "()J");
        for (int i = 0; i < size; ++i) {
            auto element = env->GetObjectArrayElement(pWaitSemaphoresArray, i);
            jlong value = env->CallLongMethod(element, getValueMethod);
            waitSemaphoresStorage.push_back(reinterpret_cast<VkSemaphore>(value));
            env->DeleteLocalRef(element);
        }
        env->DeleteLocalRef(longClass);
    }
    clazzInfo.waitSemaphoreCount = static_cast<uint32_t>(waitSemaphoresStorage.size());
    clazzInfo.pWaitSemaphores = waitSemaphoresStorage.empty() ? nullptr : waitSemaphoresStorage.data();
    env->DeleteLocalRef(pWaitSemaphoresArray);
}

VkSubmitInfoAccessor::~VkSubmitInfoAccessor() {
    env->DeleteLocalRef(clazz);
}

