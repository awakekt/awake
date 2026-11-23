/*
 *  VkPresentInfoKHRAccessor.cpp
 *  Vulkan accessor for VkPresentInfoKHR
 *  Created by Ron June Valdoz */

#include <includes/VkPresentInfoKHRAccessor.h>

VkPresentInfoKHRAccessor::VkPresentInfoKHRAccessor(JNIEnv *env, jobject obj) : env(env), obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    pWaitSemaphoresField = env->GetFieldID(clazz, "pWaitSemaphores", "[Ljava/lang/Long;");
    pSwapchainsField = env->GetFieldID(clazz, "pSwapchains", "[Ljava/lang/Long;");
    pImageIndicesField = env->GetFieldID(clazz, "pImageIndices", "[I");
    pResultsField = env->GetFieldID(clazz, "pResults",
                                    "[Lcom/awakekt/awake/vulkan/enums/VkResult;");
}

VkStructureType
VkPresentInfoKHRAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

void
VkPresentInfoKHRAccessor::getpImageIndices(VkPresentInfoKHR &clazzInfo) {
    imageIndicesStorage.clear();
    auto pImageIndicesArray = (jintArray) env->GetObjectField(obj, pImageIndicesField);
    if (pImageIndicesArray == nullptr) {
        clazzInfo.pImageIndices = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pImageIndicesArray);
    if (size > 0) {
        imageIndicesStorage.resize(static_cast<size_t>(size));
        env->GetIntArrayRegion(pImageIndicesArray, 0, size,
                               reinterpret_cast<jint *>(imageIndicesStorage.data()));
    }
    clazzInfo.pImageIndices = imageIndicesStorage.empty() ? nullptr : imageIndicesStorage.data();
    env->DeleteLocalRef(pImageIndicesArray);
}

void
VkPresentInfoKHRAccessor::getpSwapchains(VkPresentInfoKHR &clazzInfo) {
    swapchainsStorage.clear();
    auto pSwapchainsArray = (jobjectArray) env->GetObjectField(obj, pSwapchainsField);
    if (pSwapchainsArray == nullptr) {
        clazzInfo.swapchainCount = 0;
        clazzInfo.pSwapchains = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pSwapchainsArray);
    if (size > 0) {
        swapchainsStorage.reserve(static_cast<size_t>(size));
        jclass longClass = env->FindClass("java/lang/Long");
        jmethodID getValueMethod = env->GetMethodID(longClass, "longValue", "()J");
        for (int i = 0; i < size; ++i) {
            auto element = env->GetObjectArrayElement(pSwapchainsArray, i);
            jlong value = env->CallLongMethod(element, getValueMethod);
            swapchainsStorage.push_back(reinterpret_cast<VkSwapchainKHR>(value));
            env->DeleteLocalRef(element);
        }
        env->DeleteLocalRef(longClass);
    }
    clazzInfo.swapchainCount = static_cast<uint32_t>(swapchainsStorage.size());
    clazzInfo.pSwapchains = swapchainsStorage.empty() ? nullptr : swapchainsStorage.data();
    env->DeleteLocalRef(pSwapchainsArray);
}

void
VkPresentInfoKHRAccessor::getpWaitSemaphores(VkPresentInfoKHR &clazzInfo) {
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

void
VkPresentInfoKHRAccessor::getpNext(VkPresentInfoKHR &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkPresentInfoKHRAccessor::fromObject(VkPresentInfoKHR &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    getpWaitSemaphores(clazzInfo);  // Long Object Array
    getpSwapchains(clazzInfo);  // Long Object Array
    getpImageIndices(clazzInfo);  // int Object Array
    getpResults(clazzInfo);  // VkResult Object Array
}

void
VkPresentInfoKHRAccessor::getpResults(VkPresentInfoKHR &clazzInfo) {
    resultsStorage.clear();
    auto pResultsArray = (jobjectArray) env->GetObjectField(obj, pResultsField);
    if (pResultsArray == nullptr) {
        clazzInfo.pResults = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pResultsArray);
    if (size > 0) {
        resultsStorage.reserve(static_cast<size_t>(size));
        for (int i = 0; i < size; ++i) {
            auto element = env->GetObjectArrayElement(pResultsArray, i);
            resultsStorage.push_back(
                    static_cast<VkResult>(enum_utils::getEnumFromObject(env, element)));
            env->DeleteLocalRef(element);
        }
    }
    clazzInfo.pResults = resultsStorage.empty() ? nullptr : resultsStorage.data();
    env->DeleteLocalRef(pResultsArray);
}

VkPresentInfoKHRAccessor::~VkPresentInfoKHRAccessor() {
    env->DeleteLocalRef(clazz);
}

