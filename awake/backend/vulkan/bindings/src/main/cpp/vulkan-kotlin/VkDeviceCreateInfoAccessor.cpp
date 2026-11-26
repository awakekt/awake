/*
 *  VkDeviceCreateInfoAccessor.cpp
 *  Vulkan accessor for VkDeviceCreateInfo
 *  Created by Ron June Valdoz */

#include <includes/VkDeviceCreateInfoAccessor.h>
#include <includes/VulkanAccessArena.h>

VkDeviceCreateInfoAccessor::VkDeviceCreateInfoAccessor(JNIEnv *env, jobject obj) : env(env),
                                                                                   obj(obj) {
    clazz = env->GetObjectClass(obj);
    sTypeField = env->GetFieldID(clazz, "sType",
                                 "Lcom/awakekt/awake/vulkan/enums/VkStructureType;");
    pNextField = env->GetFieldID(clazz, "pNext", "Ljava/lang/Object;");
    flagsField = env->GetFieldID(clazz, "flags", "I");
    pQueueCreateInfosField = env->GetFieldID(clazz, "pQueueCreateInfos",
                                             "[Lcom/awakekt/awake/vulkan/models/info/VkDeviceQueueCreateInfo;");
    ppEnabledLayerNamesField = env->GetFieldID(clazz, "ppEnabledLayerNames", "[Ljava/lang/String;");
    ppEnabledExtensionNamesField = env->GetFieldID(clazz, "ppEnabledExtensionNames",
                                                   "[Ljava/lang/String;");
    pEnabledFeaturesField = env->GetFieldID(clazz, "pEnabledFeatures",
                                            "[Lcom/awakekt/awake/vulkan/models/physicaldevice/VkPhysicalDeviceFeatures;");
}

VkStructureType
VkDeviceCreateInfoAccessor::getsType() {
    auto sTypeEnum = (jobject) env->GetObjectField(obj, sTypeField);
    auto enumValue = (VkStructureType) enum_utils::getEnumFromObject(env, sTypeEnum);
    env->DeleteLocalRef(sTypeEnum); // release enum reference
    return enumValue;
}

uint32_t
VkDeviceCreateInfoAccessor::getflags() {
    return (uint32_t) (jint) env->GetIntField(obj, flagsField); // primitive
}

void
VkDeviceCreateInfoAccessor::fromObject(VkDeviceCreateInfo &clazzInfo) {
    clazzInfo.sType = getsType(); // Enum VkStructureType
    getpNext(clazzInfo); // Other void*
    clazzInfo.flags = getflags(); // Primitive uint32_t
    getpQueueCreateInfos(clazzInfo);  // VkDeviceQueueCreateInfo Object Array
    getppEnabledLayerNames(clazzInfo);  // String Object Array
    getppEnabledExtensionNames(clazzInfo);  // String Object Array
    getpEnabledFeatures(clazzInfo);  // VkPhysicalDeviceFeatures Object Array
}

void
VkDeviceCreateInfoAccessor::getpNext(VkDeviceCreateInfo &clazzInfo) {
    auto ref = (void *) (jobject) env->GetObjectField(obj, pNextField); // Any Object
    clazzInfo.pNext = ref;
}

void
VkDeviceCreateInfoAccessor::getppEnabledLayerNames(VkDeviceCreateInfo &clazzInfo) {
    auto ppEnabledLayerNamesArray = (jobjectArray) env->GetObjectField(obj,
                                                                       ppEnabledLayerNamesField);
    if (ppEnabledLayerNamesArray == nullptr) {
        clazzInfo.enabledLayerCount = 0;
        clazzInfo.ppEnabledLayerNames = nullptr;
        return;
    }
    auto size = env->GetArrayLength(ppEnabledLayerNamesArray);
    if (size <= 0) {
        clazzInfo.enabledLayerCount = 0;
        clazzInfo.ppEnabledLayerNames = nullptr;
        env->DeleteLocalRef(ppEnabledLayerNamesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto &arena = awake::VulkanAccessArena::current();
    auto *copy = arena.allocArray<const char *>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jstring) env->GetObjectArrayElement(ppEnabledLayerNamesArray, i);
        copy[i] = arena.copyJString(env, element);
        if (element != nullptr) {
            env->DeleteLocalRef(element);
        }
    }
    clazzInfo.enabledLayerCount = static_cast<uint32_t>(count);
    clazzInfo.ppEnabledLayerNames = copy;
    env->DeleteLocalRef(ppEnabledLayerNamesArray);
}

void
VkDeviceCreateInfoAccessor::getppEnabledExtensionNames(VkDeviceCreateInfo &clazzInfo) {
    auto ppEnabledExtensionNamesArray = (jobjectArray) env->GetObjectField(obj,
                                                                           ppEnabledExtensionNamesField);
    if (ppEnabledExtensionNamesArray == nullptr) {
        clazzInfo.enabledExtensionCount = 0;
        clazzInfo.ppEnabledExtensionNames = nullptr;
        return;
    }
    auto size = env->GetArrayLength(ppEnabledExtensionNamesArray);
    if (size <= 0) {
        clazzInfo.enabledExtensionCount = 0;
        clazzInfo.ppEnabledExtensionNames = nullptr;
        env->DeleteLocalRef(ppEnabledExtensionNamesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto &arena = awake::VulkanAccessArena::current();
    auto *copy = arena.allocArray<const char *>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jstring) env->GetObjectArrayElement(ppEnabledExtensionNamesArray, i);
        copy[i] = arena.copyJString(env, element);
        if (element != nullptr) {
            env->DeleteLocalRef(element);
        }
    }
    clazzInfo.enabledExtensionCount = static_cast<uint32_t>(count);
    clazzInfo.ppEnabledExtensionNames = copy;
    env->DeleteLocalRef(ppEnabledExtensionNamesArray);
}

void
VkDeviceCreateInfoAccessor::getpEnabledFeatures(VkDeviceCreateInfo &clazzInfo) {
    auto pEnabledFeaturesArray = (jobjectArray) env->GetObjectField(obj, pEnabledFeaturesField);
    if (pEnabledFeaturesArray == nullptr) {
        clazzInfo.pEnabledFeatures = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pEnabledFeaturesArray);
    if (size <= 0) {
        clazzInfo.pEnabledFeatures = nullptr;
        env->DeleteLocalRef(pEnabledFeaturesArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkPhysicalDeviceFeatures>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pEnabledFeaturesArray, i);
        VkPhysicalDeviceFeaturesAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.pEnabledFeatures = copy;
    env->DeleteLocalRef(pEnabledFeaturesArray);
}

void
VkDeviceCreateInfoAccessor::getpQueueCreateInfos(VkDeviceCreateInfo &clazzInfo) {
    auto pQueueCreateInfosArray = (jobjectArray) env->GetObjectField(obj, pQueueCreateInfosField);
    if (pQueueCreateInfosArray == nullptr) {
        clazzInfo.queueCreateInfoCount = 0;
        clazzInfo.pQueueCreateInfos = nullptr;
        return;
    }
    auto size = env->GetArrayLength(pQueueCreateInfosArray);
    if (size <= 0) {
        clazzInfo.queueCreateInfoCount = 0;
        clazzInfo.pQueueCreateInfos = nullptr;
        env->DeleteLocalRef(pQueueCreateInfosArray);
        return;
    }
    auto count = static_cast<std::size_t>(size);
    auto *copy = awake::VulkanAccessArena::current().allocArray<VkDeviceQueueCreateInfo>(count);
    for (int i = 0; i < size; ++i) {
        auto element = (jobject) env->GetObjectArrayElement(pQueueCreateInfosArray, i);
        VkDeviceQueueCreateInfoAccessor accessor(env, element);
        accessor.fromObject(copy[i]);
        env->DeleteLocalRef(element);
    }
    clazzInfo.queueCreateInfoCount = static_cast<uint32_t>(count);
    clazzInfo.pQueueCreateInfos = copy;
    env->DeleteLocalRef(pQueueCreateInfosArray);
}

VkDeviceCreateInfoAccessor::~VkDeviceCreateInfoAccessor() {
    env->DeleteLocalRef(clazz);
}

